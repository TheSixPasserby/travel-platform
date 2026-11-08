package com.example.travelplatform.common.ip;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.util.*;

@Component
public class ClientIpResolver {
    private final Set<InetAddress> trusted;

    public ClientIpResolver(@Value("${travel.ip.trusted-proxies:}") String proxies) {
        Set<InetAddress> values=new HashSet<>();
        if(!proxies.isBlank())for(String raw:proxies.split(",",-1)){
            InetAddress address=literal(raw.trim());
            if(address==null)throw new IllegalArgumentException("Trusted proxies must be explicit IP literals");
            values.add(address);
        }
        trusted=Set.copyOf(values);
    }

    /** Only an explicitly trusted direct peer may supply XFF. X-Real-IP/Forwarded are ignored. */
    public InetAddress resolve(HttpServletRequest request) {
        InetAddress peer=literal(request.getRemoteAddr());
        if(peer==null||!trusted.contains(peer))return peer;
        List<String> headers=Collections.list(request.getHeaders("X-Forwarded-For"));
        if(headers.isEmpty())return peer;
        String chain=String.join(",",headers);
        if(chain.length()>2048)return null;
        String[] hops=chain.split(",",-1);
        if(hops.length>20)return null;
        List<InetAddress> parsed=new ArrayList<>();
        for(String hop:hops){InetAddress address=literal(hop.trim());if(address==null)return null;parsed.add(address);}
        for(int i=parsed.size()-1;i>=0&&trusted.contains(peer);i--)peer=parsed.get(i);
        return peer;
    }

    /** Numeric-only validation before JDK IPv6 parsing: never resolve a hostname. No zones or ports. */
    static InetAddress literal(String value) {
        if(value==null||value.isEmpty()||value.length()>45)return null;
        try {
            if(value.contains(":")){
                if(!value.matches("[0-9a-fA-F:.]+"))return null;
                return InetAddress.getByName(value);
            }
            String[] parts=value.split("\\.",-1);if(parts.length!=4)return null;
            byte[] bytes=new byte[4];
            for(int i=0;i<4;i++){
                if(!parts[i].matches("0|[1-9][0-9]{0,2}"))return null;
                int n=Integer.parseInt(parts[i]);if(n>255)return null;bytes[i]=(byte)n;
            }
            return InetAddress.getByAddress(bytes);
        }catch(Exception ignored){return null;}
    }

    static boolean publicAddress(InetAddress address) {
        if(address==null||address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isSiteLocalAddress()
                ||address.isLinkLocalAddress()||address.isMulticastAddress())return false;
        byte[] bytes=address.getAddress();int a=bytes[0]&255,b=bytes[1]&255;
        if(bytes.length==16)return (a&0xe0)==0x20 && !(a==0x20&&b==1&&(bytes[2]&255)==13&&(bytes[3]&255)==184);
        int c=bytes[2]&255;
        return a!=0&&a<224&&!(a==100&&b>=64&&b<=127)&&!(a==192&&b==0&&(c==0||c==2))
                &&!(a==198&&(b==18||b==19||b==51&&c==100))&&!(a==203&&b==0&&c==113);
    }
}
