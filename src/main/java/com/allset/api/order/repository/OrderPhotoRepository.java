package com.allset.api.order.repository;

import com.allset.api.order.domain.OrderPhoto;
import com.allset.api.order.domain.PhotoType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderPhotoRepository extends JpaRepository<OrderPhoto, UUID> {

    List<OrderPhoto> findAllByOrderId(UUID orderId);

    boolean existsByOrderIdAndPhotoType(UUID orderId, PhotoType photoType);
}
