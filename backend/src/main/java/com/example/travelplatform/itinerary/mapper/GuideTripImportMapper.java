package com.example.travelplatform.itinerary.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface GuideTripImportMapper {
    record Receipt(String requestHash, String responseJson, long itineraryId, long dayId) { }

    @Select("SELECT request_hash,response_json,itinerary_id,day_id FROM itinerary_guide_imports WHERE user_id=#{user} AND client_request_id=#{key}")
    Receipt find(@Param("user") long user, @Param("key") String key);

    @Insert("""
        INSERT INTO itinerary_guide_imports(user_id,client_request_id,request_hash,itinerary_id,day_id,response_json)
        VALUES (#{user},#{key},#{hash},#{root},#{day},#{json})
        """)
    void insert(@Param("user") long user, @Param("key") String key, @Param("hash") String hash,
                @Param("root") long root, @Param("day") long day, @Param("json") String json);
}
