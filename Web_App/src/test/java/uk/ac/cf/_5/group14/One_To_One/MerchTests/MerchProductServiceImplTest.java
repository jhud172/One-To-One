package uk.ac.cf._5.group14.One_To_One.MerchTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import uk.ac.cf._5.group14.One_To_One.Merch.MerchProduct;
import uk.ac.cf._5.group14.One_To_One.Merch.MerchProductRepository;
import uk.ac.cf._5.group14.One_To_One.Merch.MerchProductServiceImpl;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderService;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderItemRepository;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchProductServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void saveWithImage_storesSanitizedImageUsingDetectedSignature() throws Exception {
        MerchProductRepository repo = mock(MerchProductRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MerchProductServiceImpl service = service(repo, mock(MerchOrderService.class));
        MerchProduct product = new MerchProduct();
        product.setName("Training Tee");

        MockMultipartFile upload = new MockMultipartFile(
                "image",
                "tee.txt",
                "text/plain",
                pngBytes());

        MerchProduct saved = service.saveWithImage(product, upload);

        assertThat(saved.getImageUrl()).startsWith("/uploads/merch/product-").endsWith(".png");
        assertThat(tempDir.resolve(Path.of(saved.getImageUrl().substring("/uploads/merch/".length())).getFileName())).exists();
    }

    @Test
    void saveWithImageRejectsOversizedDimensionsBeforeDecodingPixels() throws Exception {
        byte[] compressed = pngBytes();
        java.nio.ByteBuffer.wrap(compressed).putInt(16, 100_000).putInt(20, 100_000);
        java.util.zip.CRC32 checksum = new java.util.zip.CRC32();
        checksum.update(compressed, 12, 17);
        java.nio.ByteBuffer.wrap(compressed).putInt(29, (int) checksum.getValue());
        MerchProduct product = new MerchProduct();
        product.setName("Fixture product");
        MockMultipartFile upload = new MockMultipartFile("image", "compressed.png", "image/png", compressed);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> service(mock(MerchProductRepository.class), mock(MerchOrderService.class)).saveWithImage(product, upload));
        assertThat(failure.getMessage()).contains("dimensions");
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void saveWithImage_rejectsFakeImagePayload() {
        MerchProductRepository repo = mock(MerchProductRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MerchProductServiceImpl service = service(repo, mock(MerchOrderService.class));
        MerchProduct product = new MerchProduct();
        product.setName("Training Tee");

        MockMultipartFile upload = new MockMultipartFile(
                "image",
                "tee.png",
                "image/png",
                "not-an-image".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.saveWithImage(product, upload));
        assertThat(ex.getMessage()).contains("Unsupported image");
    }

    @Test
    void deleteProduct_removesItsStoredImageWhenNoOrderHistoryNeedsIt() throws Exception {
        MerchProductRepository repo = mock(MerchProductRepository.class);
        MerchOrderService orderService = mock(MerchOrderService.class);
        MerchProduct product = new MerchProduct();
        product.setId(44L);
        product.setActive(true);
        product.setImageUrl("/uploads/merch/product-synthetic.png");
        Files.write(tempDir.resolve("product-synthetic.png"), pngBytes());
        when(repo.findById(44L)).thenReturn(Optional.of(product));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MerchProductServiceImpl service = service(repo, orderService);
        service.deleteProduct(44L);

        assertThat(product.isActive()).isFalse();
        assertThat(product.getImageUrl()).isNull();
        assertThat(tempDir.resolve("product-synthetic.png")).doesNotExist();
    }

    @Test
    void saveWithImage_rejectsImageLargerThanFiveMiB() {
        MerchProductRepository repo = mock(MerchProductRepository.class);
        MerchProduct product = new MerchProduct();
        product.setName("Training Tee");
        MockMultipartFile upload = new MockMultipartFile(
                "image",
                "oversized.png",
                "image/png",
                new byte[(5 * 1024 * 1024) + 1]);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service(repo, mock(MerchOrderService.class)).saveWithImage(product, upload));

        assertThat(error.getMessage()).contains("5 MB");
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void deleteProduct_retainsStoredImageReferencedByAnOrderSnapshot() throws Exception {
        MerchProductRepository repo = mock(MerchProductRepository.class);
        MerchOrderItemRepository orderItems = mock(MerchOrderItemRepository.class);
        MerchProduct product = new MerchProduct();
        product.setId(45L);
        product.setActive(true);
        product.setImageUrl("/uploads/merch/product-ordered.png");
        Files.write(tempDir.resolve("product-ordered.png"), pngBytes());
        when(repo.findById(45L)).thenReturn(Optional.of(product));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderItems.existsByImageUrlSnapshot("/uploads/merch/product-ordered.png")).thenReturn(true);

        service(repo, mock(MerchOrderService.class), orderItems).deleteProduct(45L);

        assertThat(product.isActive()).isFalse();
        assertThat(product.getImageUrl()).isNull();
        assertThat(tempDir.resolve("product-ordered.png")).exists();
    }

    @Test
    void saveWithImage_removesNewFileWhenRepositorySaveFails() throws Exception {
        MerchProductRepository repo = mock(MerchProductRepository.class);
        when(repo.save(any())).thenThrow(new IllegalStateException("synthetic persistence failure"));
        MerchProduct product = new MerchProduct();
        product.setName("Training Tee");
        MockMultipartFile upload = new MockMultipartFile(
                "image",
                "tee.png",
                "image/png",
                pngBytes());

        assertThrows(
                IllegalStateException.class,
                () -> service(repo, mock(MerchOrderService.class)).saveWithImage(product, upload));

        assertThat(tempDir).isEmptyDirectory();
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(6, 6, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, Color.CYAN.getRGB());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private MerchProductServiceImpl service(MerchProductRepository repo, MerchOrderService orderService) {
        return service(repo, orderService, mock(MerchOrderItemRepository.class));
    }

    private MerchProductServiceImpl service(MerchProductRepository repo,
                                            MerchOrderService orderService,
                                            MerchOrderItemRepository orderItemRepository) {
        return new MerchProductServiceImpl(
                repo,
                orderService,
                orderItemRepository,
                tempDir.toString());
    }
}
