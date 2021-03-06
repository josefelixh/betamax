package betamax.proxy

import betamax.Recorder
import betamax.util.server.EchoHandler
import org.apache.http.impl.conn.ProxySelectorRoutePlanner
import betamax.proxy.jetty.*
import groovyx.net.http.*
import static java.net.HttpURLConnection.HTTP_FORBIDDEN
import spock.lang.*

@Issue("https://github.com/robfletcher/betamax/issues/18")
class NoTapeSpec extends Specification {

	@Shared @AutoCleanup("restoreOriginalProxySettings") Recorder recorder = new Recorder()
	@Shared @AutoCleanup("stop") ProxyServer proxy = new ProxyServer()
	@Shared @AutoCleanup("stop") SimpleServer endpoint = new SimpleServer()
	RESTClient http

	def setupSpec() {
		proxy.start(recorder)
		recorder.overrideProxySettings()
		endpoint.start(EchoHandler)
	}

	def setup() {
		http = new RESTClient(endpoint.url)
		http.client.routePlanner = new ProxySelectorRoutePlanner(http.client.connectionManager.schemeRegistry, ProxySelector.default)
	}

	def "an error is returned if the proxy intercepts a request when no tape is inserted"() {
		when:
		http.get(path: "/")

		then:
		def e = thrown(HttpResponseException)
		e.statusCode == HTTP_FORBIDDEN
		e.message == "No tape"
	}
}
