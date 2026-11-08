package com.example.travelplatform.common.ip;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestIpRegion {
    private final ClientIpResolver clients;
    private final IpRegionResolver regions;
    public RequestIpRegion(ClientIpResolver clients,IpRegionResolver regions){this.clients=clients;this.regions=regions;}

    public String current() {
        try {
            if(!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))return null;
            var address=clients.resolve(attributes.getRequest());
            if(!ClientIpResolver.publicAddress(address))return null;
            String region=regions.resolve(address);
            // Defensive output validation prevents an accidental raw IP/coordinates/path from becoming public.
            return region!=null&&region.matches("[\\p{L}\\p{M} ·-]{1,64}")&&!region.isBlank()?region.strip():null;
        }catch(RuntimeException ignored){return null;}
    }
}
