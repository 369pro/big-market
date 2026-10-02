package com.pro.infrastructure.dao;

import com.pro.infrastructure.dao.po.Award;
import org.apache.ibatis.annotations.Mapper;

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

    /** Read one award definition from the default configuration database. */
    Award queryAwardByAwardId(Integer awardId);

    String queryAwardConfigByAwardId(Integer awardId);

    String queryAwardKeyByAwardId(Integer awardId);
}
