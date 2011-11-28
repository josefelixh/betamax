package betamax.proxy

import betamax.proxy.jetty.SimpleServer
import betamax.util.server.EchoHandler
import java.security.KeyStore
import java.security.cert.X509Certificate
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.conn.ProxySelectorRoutePlanner
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector
import org.junit.Rule
import betamax.*
import javax.net.ssl.*
import static org.apache.http.HttpHeaders.VIA
import static org.apache.http.HttpVersion.HTTP_1_1
import org.apache.http.conn.scheme.*
import org.apache.http.conn.ssl.*
import org.apache.http.params.*
import static org.apache.http.protocol.HTTP.UTF_8
import org.eclipse.jetty.server.*
import spock.lang.*
import groovy.transform.InheritConstructors
import org.apache.http.HttpStatus
import static org.apache.http.HttpStatus.SC_OK

@Issue("https://github.com/robfletcher/betamax/issues/34")
class HttpsSpec extends Specification {

	@Shared @AutoCleanup("deleteDir") File tapeRoot = new File(System.properties."java.io.tmpdir", "tapes")
	@Rule @AutoCleanup("ejectTape") Recorder recorder = new Recorder(tapeRoot: tapeRoot)
	@Shared @AutoCleanup("stop") SimpleServer httpsEndpoint = new SimpleSecureServer(5001)
	@Shared @AutoCleanup("stop") SimpleServer httpEndpoint = new SimpleServer()

	@Shared URI httpUri
	@Shared URI httpsUri

	HttpClient http

	def setupSpec() {
		httpEndpoint.start(EchoHandler)
		httpsEndpoint.start(EchoHandler)

		httpUri = httpEndpoint.url.toURI()
		httpsUri = httpsEndpoint.url.toURI()
	}

	def setup() {
		def trustStore = KeyStore.getInstance(KeyStore.defaultType)
		trustStore.load(null, null)

		def sslSocketFactory = new DummySSLSocketFactory(trustStore)
		sslSocketFactory.hostnameVerifier = new X509HostnameVerifier() {
			void verify(String host, SSLSocket ssl) {
				//To change body of implemented methods use File | Settings | File Templates.
			}

			void verify(String host, X509Certificate cert) {
				//To change body of implemented methods use File | Settings | File Templates.
			}

			void verify(String host, String[] cns, String[] subjectAlts) {
				//To change body of implemented methods use File | Settings | File Templates.
			}

			boolean verify(String s, SSLSession sslSession) {
				true
			}
		}

		def params = new BasicHttpParams()
		HttpProtocolParams.setVersion(params, HTTP_1_1)
		HttpProtocolParams.setContentCharset(params, UTF_8)

		def registry = new SchemeRegistry()
		registry.register new Scheme("http", PlainSocketFactory.socketFactory, 80)
		registry.register new Scheme("https", sslSocketFactory, 443)

		def connectionManager = new ThreadSafeClientConnManager(params, registry)

		http = new DefaultHttpClient(connectionManager, params)
		http.routePlanner = new ProxySelectorRoutePlanner(http.connectionManager.schemeRegistry, ProxySelector.default)
	}

	@Betamax(tape = "https spec")
	@Unroll("proxy is selected for #scheme URIs")
	def "proxy is selected for all URIs"() {
		given:
		def proxySelector = ProxySelector.default

		expect:
		def proxy = proxySelector.select(uri).first()
		proxy.type() == Proxy.Type.HTTP
		proxy.address().toString() == "$recorder.proxyHost:${scheme == 'https' ? recorder.httpsProxyPort : recorder.proxyPort}"

		where:
		uri << [httpUri, httpsUri]
		scheme = uri.scheme
	}

	@Betamax(tape = "https spec")
	def "proxy can intercept HTTP requests"() {
		when: "an HTTPS request is made"
		def response = http.execute(new HttpGet(httpEndpoint.url))

		then: "it is intercepted by the proxy"
		response.statusLine.statusCode == SC_OK
		response.getFirstHeader(VIA)?.value == "Betamax"
	}

	@Betamax(tape = "https spec")
	def "proxy can intercept HTTPS requests"() {
		when: "an HTTPS request is made"
		def response = http.execute(new HttpGet(httpsEndpoint.url))

		then: "it is intercepted by the proxy"
		response.statusLine.statusCode == SC_OK
		response.getFirstHeader(VIA)?.value == "Betamax"
	}

}

class DummySSLSocketFactory extends SSLSocketFactory {
	SSLContext sslContext = SSLContext.getInstance("TLS")

	public DummySSLSocketFactory(KeyStore trustStore) {
		super(trustStore)

		def trustManager = new X509TrustManager() {
			void checkClientTrusted(X509Certificate[] chain, String authType) { }

			void checkServerTrusted(X509Certificate[] chain, String authType) { }

			X509Certificate[] getAcceptedIssuers() {
				null
			}
		}

		sslContext.init(null, [trustManager] as TrustManager[], null)
	}

	@Override
	Socket createSocket(Socket socket, String host, int port, boolean autoClose) {
		sslContext.socketFactory.createSocket(socket, host, port, autoClose)
	}

	@Override
	Socket createSocket() throws IOException {
		sslContext.socketFactory.createSocket()
	}
}

@InheritConstructors
class SimpleSecureServer extends SimpleServer {

	@Override
	String getUrl() {
		"https://$host:$port/"
	}

	@Override
	protected Server createServer(int port) {
		def server = super.createServer(port)

		def connector = new SslSelectChannelConnector()

		String keystore = new File("src/test/resources/keystore").absolutePath

		connector.port = port
		connector.keystore = keystore
		connector.password = "password"
		connector.keyPassword = "password"

		server.connectors = [connector] as Connector[]

		server
	}
}