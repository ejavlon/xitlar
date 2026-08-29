package uz.xitlar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.xitlar.entity.Image;

@Repository
public interface ImageRepository extends JpaRepository<Image, Integer> {
}
