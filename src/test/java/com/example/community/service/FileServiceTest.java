package com.example.community.service;

import com.example.community.common.FilePolicy;
import com.example.community.repository.PostImageRepository;
import com.example.community.service.dto.ImageMeta;
import com.example.community.storage.Storage;
import com.example.community.storage.Storage.StoredFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

        @Mock
        private Storage storage;

        @Mock
        private PostImageRepository postImageRepository;

        @InjectMocks
        private FileService fileService;

        @Test
        @DisplayName("uploadFilesToStorage는 모든 파일을 처리하고 결과 목록을 반환")
        void uploadFilesToStorage_processes_all_files_and_returns_results() throws Exception {
                // 테스트 데이터 준비
                Long memberId = 123L;
                MockMultipartFile file1 = new MockMultipartFile(
                                "file1", "test1.png", "image/png",
                                new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 });
                MockMultipartFile file2 = new MockMultipartFile(
                                "file2", "test2.jpg", "image/jpeg",
                                new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0 });

                List<MultipartFile> files = Arrays.asList(file1, file2);

                // Storage 모의 객체 설정
                StoredFile storedFile1 = new StoredFile(
                                "posts/123/file1.png", "test1.png", "image/png", 8,
                                "http://localhost:8080/files/posts/123/file1.png");
                StoredFile storedFile2 = new StoredFile(
                                "posts/123/file2.jpg", "test2.jpg", "image/jpeg", 8,
                                "http://localhost:8080/files/posts/123/file2.jpg");

                // storeWithKey 메서드가 호출될 때 반환값 설정
                when(storage.storeWithKey(eq(file1), any(String.class))).thenReturn(storedFile1);
                when(storage.storeWithKey(eq(file2), any(String.class))).thenReturn(storedFile2);

                // 메서드 호출
                List<String> keys = fileService.uploadPostImageKeys(files, memberId);

                // 검증
                assertThat(keys).hasSize(2);
                assertThat(keys).contains(storedFile1.key(), storedFile2.key());

                // storage.storeWithKey가 각 파일에 대해 호출되었는지 확인
                verify(storage).storeWithKey(eq(file1), any(String.class));
                verify(storage).storeWithKey(eq(file2), any(String.class));
        }

        @Test
        @DisplayName("uploadPostImages는 이미지 메타데이터 목록을 반환")
        @SuppressWarnings("deprecation") // 테스트 목적으로 Deprecated 메서드 사용
        void uploadPostImages_returns_image_metadata_list() throws Exception {
                // 테스트 데이터 준비
                Long memberId = 123L;
                MockMultipartFile file = new MockMultipartFile(
                                "file", "test.png", "image/png",
                                new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 });

                List<MultipartFile> files = Arrays.asList(file);

                // Storage 모의 객체 설정
                String fileKey = String.format("%s/%d/%s", FilePolicy.POST_IMAGES_PATH, memberId, "generatedName.png");
                StoredFile storedFile = new StoredFile(
                                fileKey, "test.png", "image/png", 8, "http://localhost:8080/files/" + fileKey);

                when(storage.storeWithKey(eq(file), any(String.class))).thenReturn(storedFile);

                // 메서드 호출
                List<ImageMeta> imageMetas = fileService.uploadPostImages(files, memberId);

                // 검증
                assertThat(imageMetas).hasSize(1);
                assertThat(imageMetas.get(0).key()).isEqualTo(fileKey);
                assertThat(imageMetas.get(0).url()).isEqualTo("http://localhost:8080/files/" + fileKey);
        }
}
