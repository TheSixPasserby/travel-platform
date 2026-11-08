package com.example.travelplatform.common;

import com.example.travelplatform.common.ip.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.assertj.core.api.Assertions.*;

class RequestIpRegionTest {
    @AfterEach void reset(){RequestContextHolder.resetRequestAttributes();}
    private RequestIpRegion service(String peer,IpRegionResolver regions){
        var request=new MockHttpServletRequest();request.setRemoteAddr(peer);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return new RequestIpRegion(new ClientIpResolver(""),regions);
    }
    @Test void missingProviderOrMissingRequestRemainsUnknown(){
        assertThat(new RequestIpRegion(new ClientIpResolver(""),a->"四川").current()).isNull();
        assertThat(service("1.1.1.1",a->null).current()).isNull();
    }
    @Test void privateReservedAndInvalidAddressesNeverReachProvider(){
        for(String peer:new String[]{"127.0.0.1","::1","10.0.0.1","172.16.0.1","192.168.1.1","fc00::1","fe80::1","::ffff:192.168.1.1","100.64.0.1","192.0.2.1","2001:db8::1","invalid"})
            assertThat(service(peer,a->{throw new AssertionError("private IP reached provider");}).current()).as(peer).isNull();
    }
    @Test void knownCoarseResultsAreSafeAndProviderFailuresDoNotBlockWrites(){
        assertThat(service("1.1.1.1",a->"四川").current()).isEqualTo("四川");
        assertThat(service("2606:4700:4700::1111",a->"United States").current()).isEqualTo("United States");
        assertThat(service("1.1.1.1",a->{throw new IllegalStateException();}).current()).isNull();
        for(String invalid:new String[]{"1.1.1.1","30.1,104.2","<script>","/internal/path"," ","川".repeat(65)})
            assertThat(service("1.1.1.1",a->invalid).current()).isNull();
    }
}
