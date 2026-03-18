package com.root.aishopback.vo;

import java.math.BigDecimal;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ProductVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String image;
    private List<String> images = new ArrayList<>();
    private String category;
    private String categoryLevel1;
    private String categoryLevel2;
    private String categoryLevel3;
    private String categoryPath;
    private Integer sales;
    private BigDecimal rating;
    private Integer stock;
    private List<String> tags = new ArrayList<>();
    private String description;
    private String brand;
    private String merchantName;
    private String currency;
    private Integer buyerCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCategoryLevel1() { return categoryLevel1; }
    public void setCategoryLevel1(String categoryLevel1) { this.categoryLevel1 = categoryLevel1; }
    public String getCategoryLevel2() { return categoryLevel2; }
    public void setCategoryLevel2(String categoryLevel2) { this.categoryLevel2 = categoryLevel2; }
    public String getCategoryLevel3() { return categoryLevel3; }
    public void setCategoryLevel3(String categoryLevel3) { this.categoryLevel3 = categoryLevel3; }
    public String getCategoryPath() { return categoryPath; }
    public void setCategoryPath(String categoryPath) { this.categoryPath = categoryPath; }
    public Integer getSales() { return sales; }
    public void setSales(Integer sales) { this.sales = sales; }
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Integer getBuyerCount() { return buyerCount; }
    public void setBuyerCount(Integer buyerCount) { this.buyerCount = buyerCount; }
}
