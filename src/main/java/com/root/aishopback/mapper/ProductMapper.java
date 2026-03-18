package com.root.aishopback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.root.aishopback.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    @Update("UPDATE products SET stock = stock - #{quantity}, sales = COALESCE(sales, 0) + #{quantity}, " +
        "buyer_count = COALESCE(buyer_count, sales, 0) + #{quantity} " +
        "WHERE id = #{productId} AND stock >= #{quantity}")
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
