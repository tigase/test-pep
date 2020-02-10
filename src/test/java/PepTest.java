import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.criteria.Criteria;
import tigase.jaxmpp.core.client.criteria.ElementCriteria;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.adhoc.AdHocCommansModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageCarbonsModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubErrorCondition;
import tigase.jaxmpp.core.client.xmpp.modules.pubsub.PubSubModule;
import tigase.jaxmpp.core.client.xmpp.modules.registration.InBandRegistrationModule;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterModule;
import tigase.jaxmpp.core.client.xmpp.modules.vcard.VCardModule;
import tigase.jaxmpp.core.client.xmpp.modules.xep0136.MessageArchivingModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.StreamPacket;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;
import tigase.tests.Mutex;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.testng.AssertJUnit.assertTrue;

public class PepTest {

	private BareJID jid = BareJID.bareJIDInstance(System.getProperty("account"));
	private String password = System.getProperty("password");
	private Jaxmpp jaxmpp;

	private List<Element> bookmarks = new ArrayList<>();

	private final static TrustManager[] dummyTrustManagers = new X509TrustManager[]{new X509TrustManager() {

		@Override
		public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
	}};

	@BeforeTest
	public void prepareTest() throws JaxmppException, InterruptedException {
		// create XMPP client
		jaxmpp = new Jaxmpp();
		jaxmpp.getSessionObject().setUserProperty(SocketConnector.TRUST_MANAGERS_KEY, dummyTrustManagers);
		jaxmpp.getSessionObject().setUserProperty(SocketConnector.COMPRESSION_DISABLED_KEY, Boolean.TRUE);

		jaxmpp.getConnectionConfiguration().setUserJID(jid);
		jaxmpp.getConnectionConfiguration().setUserPassword(password);

		jaxmpp.getModulesManager().register(new InBandRegistrationModule());
		jaxmpp.getModulesManager().register(new MessageModule());
		jaxmpp.getModulesManager().register(new MessageCarbonsModule());
		jaxmpp.getModulesManager().register(new MucModule());
		jaxmpp.getModulesManager().register(new AdHocCommansModule());
		jaxmpp.getModulesManager().register(new RosterModule());
		jaxmpp.getModulesManager().register(new MessageArchivingModule());
		jaxmpp.getModulesManager().register(new PubSubModule());
		jaxmpp.getModulesManager().register(new VCardModule());

		tigase.jaxmpp.j2se.Presence.initialize(jaxmpp);
		jaxmpp.getModulesManager().register(new TestModule());
		jaxmpp.getProperties().setUserProperty(Connector.SEE_OTHER_HOST_KEY, Boolean.FALSE);
		jaxmpp.getEventBus().addHandler(SocketConnector.StanzaReceivedHandler.StanzaReceivedEvent.class, new Connector.StanzaReceivedHandler() {
			@Override
			public void onStanzaReceived(SessionObject sessionObject, StreamPacket stanza) {
				try {
					TestLogger.log(" >> "+stanza.getAsString());
				} catch (XMLException e) {
					e.printStackTrace();
				}
			}
		});
		jaxmpp.getEventBus().addHandler(SocketConnector.StanzaSendingHandler.StanzaSendingEvent.class, new Connector.StanzaSendingHandler() {
			@Override
			public void onStanzaSending(SessionObject sessionObject, Element stanza) throws JaxmppException {
				try {
					TestLogger.log(" << "+stanza.getAsString());
				} catch (XMLException e) {
					e.printStackTrace();
				}
			}

		});

		jaxmpp.login(true);
		assertTrue(jaxmpp.isConnected());
	}

	@Test
	public void publish() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		PubSubModule.NotificationReceivedHandler handler = new PubSubModule.NotificationReceivedHandler() {
			@Override
			public void onNotificationReceived(SessionObject sessionObject, Message message, JID jid, String node,
											   String itemId, Element element, Date date, String s2) {
				if (PepTest.this.jid.equals(jid.getBareJid()) && "test-node".equals(node)) {
					mutex.notify("publish:notification:received");
				}
			}
		};
		jaxmpp.getModule(PubSubModule.class).addNotificationReceivedHandler(handler);

		Element payload = ElementFactory.create("payload", "Some payload for testing", null);
		jaxmpp.getModulesManager().getModule(PubSubModule.class).publishItem(jid,
																			 "test-node", "current", payload, new PubSubModule.PublishAsyncCallback() {
					@Override
					public void onPublish(String s) {
						mutex.notify("publish:success", "publish");

					}

					@Override
					protected void onEror(IQ iq, XMPPException.ErrorCondition errorCondition,
										  PubSubErrorCondition pubSubErrorCondition) throws JaxmppException {
						mutex.notify("publish:error:" + errorCondition, "publish");

					}

					@Override
					public void onTimeout() throws JaxmppException {
						mutex.notify("publish:timeout", "publish");
					}
				});

		mutex.waitFor(30*1000, "publish");
		assertTrue("Bookmarks publication failed!", mutex.isItemNotified("publish:success"));

		Thread.sleep(100);

		assertTrue("Not received notification on publication", mutex.isItemNotified("publish:notification:received"));

		jaxmpp.getModulesManager().getModule(PubSubModule.class).removeNotificationReceivedHandler(handler);
	}

	@Test(dependsOnMethods = {"publish"})
	public void reloginAndCheckNotifications() throws JaxmppException, InterruptedException {
		final Mutex mutex = new Mutex();

		jaxmpp.disconnect(true);
		Thread.sleep(100);
		assertTrue(!jaxmpp.isConnected());

		PubSubModule.NotificationReceivedHandler handler = new PubSubModule.NotificationReceivedHandler() {
			@Override
			public void onNotificationReceived(SessionObject sessionObject, Message message, JID jid, String node,
											   String itemId, Element element, Date date, String s2) {
				if (PepTest.this.jid.equals(jid.getBareJid()) && "test-node".equals(node)) {
					mutex.notify("publish:notification:received");
				}
			}
		};
		jaxmpp.getModule(PubSubModule.class).addNotificationReceivedHandler(handler);

		jaxmpp.login(true);
		assertTrue(jaxmpp.isConnected());

		mutex.waitFor(10 * 1000, "publish:notification:received");
		assertTrue("Not received notification on subscription using CAPS!",
				   mutex.isItemNotified("publish:notification:received"));
	}

	private class TestModule implements XmppModule {
		public TestModule() {
		}

		public Criteria getCriteria() {
			return ElementCriteria.empty();
		}

		public String[] getFeatures() {
			return new String[]{"test-node+notify"};
		}

		public void process(Element element) throws XMPPException, XMLException, JaxmppException {
		}
	}
}
