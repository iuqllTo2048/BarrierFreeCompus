package cn.barrierfreecampus.mapdata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.io.Serializable;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DatasetMapper extends BaseMapper<DatasetEntity> {
    @Override
    DatasetEntity selectById(@Param("id") Serializable id);

    @Update("UPDATE dataset SET enabled = #{enabled}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateEnabled(@Param("id") UUID id, @Param("enabled") boolean enabled);
}
