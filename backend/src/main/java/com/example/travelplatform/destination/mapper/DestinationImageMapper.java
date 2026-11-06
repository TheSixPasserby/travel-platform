package com.example.travelplatform.destination.mapper;
import com.example.travelplatform.destination.validation.DestinationImageValues;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface DestinationImageMapper {
    @Select("<script>SELECT id,destination_id,image_url,alt_text,sort_order,source_url,author,license,license_url,retrieved_at FROM destination_images WHERE destination_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY destination_id,sort_order,id</script>")
    List<DestinationImageRow> forDestinations(@Param("ids") List<Long> ids);
    @Delete("DELETE FROM destination_images WHERE destination_id=#{id}") int deleteForDestination(@Param("id") long id);
    @Insert("INSERT INTO destination_images(destination_id,image_url,alt_text,sort_order,source_url,author,license,license_url,retrieved_at) VALUES(#{id},#{value.url},#{value.altText},#{position},#{value.sourceUrl},#{value.author},#{value.license},#{value.licenseUrl},#{value.retrievedAt})")
    int add(@Param("id") long id,@Param("value") DestinationImageValues value,@Param("position") int position);
}
