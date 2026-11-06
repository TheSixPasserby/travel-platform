package com.example.travelplatform.destination.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.travelplatform.destination.entity.Destination;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface DestinationMapper extends BaseMapper<Destination> {
    /** Lock the complete graph with a current read, including logical-deleted rows. */
    @Select("SELECT * FROM destinations ORDER BY id FOR UPDATE")
    List<Destination> lockGraph();
    @Select("SELECT COUNT(*) FROM guide_destinations WHERE destination_id=#{id}")
    long guideLinks(long id);
}
