package com.root.aishopback.vo;

import java.math.BigDecimal;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ProductVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String sku;
    private String name;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String image;
    private List<String> images = new ArrayList<>();
    private String category;
    private Integer sales;
    private BigDecimal rating;
    private Integer stock;
    private List<String> tags = new ArrayList<>();
    private String description;
    private String brand;
    private String store;
    private String currency;
    private String availability;
    private String sourceUrl;
    private String mpn;
    private String gtin;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
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
    public String getStore() { return store; }
    public void setStore(String store) { this.store = store; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getMpn() { return mpn; }
    public void setMpn(String mpn) { this.mpn = mpn; }
    public String getGtin() { return gtin; }
    public void setGtin(String gtin) { this.gtin = gtin; }
}
