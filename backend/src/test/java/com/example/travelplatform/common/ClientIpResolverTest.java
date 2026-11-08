package com.example.travelplatform.common;

import com.example.travelplatform.common.ip.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class ClientIpResolverTest {
    private MockHttpServletRequest request(String peer, String chain) {
        var r=new MockHttpServletRequest();r.setRemoteAddr(peer);
        if(chain!=null)r.addHeader("X-Forwarded-For",chain);
        r.addHeader("X-Real-IP","8.8.4.4");return r;
    }
    @Test void ignoresAllForwardedHeadersFromUntrustedPeers(){
        assertThat(new ClientIpResolver("").resolve(request("1.1.1.1","8.8.8.8")).getHostAddress()).isEqualTo("1.1.1.1");
    }
    @Test void walksTrustedChainFromRightAndStopsAtFirstUntrustedHop(){
        var resolver=new ClientIpResolver("127.0.0.1,10.0.0.1");
        assertThat(resolver.resolve(request("127.0.0.1","8.8.4.4,1.1.1.1,10.0.0.1")).getHostAddress()).isEqualTo("1.1.1.1");
    }
    @Test void normalizesMappedIpv4AndSupportsIpv6(){
        var resolver=new ClientIpResolver("::ffff:127.0.0.1");
        assertThat(resolver.resolve(request("127.0.0.1","::ffff:8.8.8.8")).getHostAddress()).isEqualTo("8.8.8.8");
        assertThat(resolver.resolve(request("::1",null)).isLoopbackAddress()).isTrue();
        assertThat(resolver.resolve(request("2606:4700:4700::1111",null)).getAddress()).hasSize(16);
    }
    @Test void malformedChainsReturnUnknownWithoutDnsOrFallbackToSpoofedValues(){
        var resolver=new ClientIpResolver("127.0.0.1");
        for(String chain:new String[]{"8.8.8.8,example.com","1.2.3.999","8.8.8.8,", "[::1]:99","fe80::1%eth0","unknown","1.1.1.1,".repeat(30)})
            assertThat(resolver.resolve(request("127.0.0.1",chain))).as(chain).isNull();
        assertThat(resolver.resolve(request("example.com",null))).isNull();
    }
    @Test void invalidProxyConfigurationFailsClosed(){
        assertThatThrownBy(()->new ClientIpResolver("*" )).isInstanceOf(IllegalArgumentException.class);
    }
}
