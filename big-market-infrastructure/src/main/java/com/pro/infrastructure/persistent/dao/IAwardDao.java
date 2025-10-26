package com.pro.infrastructure.persistent.dao;

import com.pro.infrastructure.persistent.po.Award;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 奖品表DAO
 * @create 2023-12-16 13:23
 */
@Mapper
public interface IAwardDao {
    // @Select("SELECT * FROM award")
    List<Award> queryAwardList();

}
