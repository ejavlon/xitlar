package uz.xitlar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.xitlar.entity.Album;

public interface AlbumRepository extends JpaRepository<Album, Integer> {
}
