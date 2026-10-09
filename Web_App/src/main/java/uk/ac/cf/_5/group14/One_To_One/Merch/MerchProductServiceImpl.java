package uk.ac.cf._5.group14.One_To_One.Merch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderService;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderItemRepository;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class MerchProductServiceImpl implements MerchProductService {

    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 16_000_000;
    private static final int MAX_IMAGE_DIMENSION = 8192;

    private final MerchProductRepository productRepo;
    private final MerchOrderService orderService;
    private final MerchOrderItemRepository orderItemRepository;
    private final Path uploadRoot;

    public MerchProductServiceImpl(MerchProductRepository productRepo,
                                   @Lazy MerchOrderService orderService,
                                   MerchOrderItemRepository orderItemRepository,
                                   @Value("${app.storage.merch-dir:uploads/merch}") String uploadRoot) {
        this.productRepo = productRepo;
        this.orderService = orderService;
        this.orderItemRepository = orderItemRepository;
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveProducts() { return productRepo.countByActiveTrue(); }

    @Override
    @Transactional(readOnly = true)
    public List<String> getActiveCategories() {
        return productRepo.findActiveCategories().stream().filter(value -> !value.isBlank()).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<MerchProduct> searchCatalogue(String search, String category, boolean inStock, int page) {
        if (page < 1 || (search != null && search.length() > 120) || (category != null && category.length() > 100)) {
            throw new IllegalArgumentException("Invalid catalogue filters.");
        }
        String term = search == null || search.isBlank() ? null : search.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        String type = category == null || category.isBlank() ? null : category;
        long count = productRepo.countCatalogue(term, type, inStock);
        int pages = (int) Math.max(1, Math.min(Integer.MAX_VALUE, (count + 19) / 20));
        int current = Math.min(page, pages);
        var request = org.springframework.data.domain.PageRequest.of(current - 1, 20);
        return new org.springframework.data.domain.PageImpl<>(productRepo.findCatalogue(term, type, inStock, request), request, count);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MerchProduct> getActiveProducts() {
        return productRepo.findByActiveTrueOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MerchProduct> getAllProducts() {
        return productRepo.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MerchProduct> findById(Long id) {
        return productRepo.findById(id);
    }

    @Override
    public MerchProduct save(MerchProduct product) {
        return productRepo.save(product);
    }

    @Override
    public MerchProduct saveWithImage(MerchProduct product, MultipartFile image) {
        if (image != null && !image.isEmpty()) {
            String previousImageUrl = product.getImageUrl();
            String storedImageUrl;
            try {
                storedImageUrl = storeSanitizedImage(image);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to store product image", e);
            }
            product.setImageUrl(storedImageUrl);
            try {
                MerchProduct saved = productRepo.save(product);
                deleteStoredImageIfUnreferenced(previousImageUrl);
                return saved;
            } catch (RuntimeException failure) {
                deleteStoredImage(storedImageUrl);
                throw failure;
            }
        }
        return productRepo.save(product);
    }

    @Override
    public boolean decrementStock(Long productId, int qty) {
        if (productId == null || qty < 1) return false;
        return productRepo.decrementStock(productId, qty) == 1;
    }

    @Override
    public void incrementStock(Long productId, int qty) {
        if (productId == null || qty < 1) {
            return;
        }
        productRepo.incrementStock(productId, qty);
    }

    @Override
    public void deleteProduct(Long productId) {
        MerchProduct product = productRepo.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        orderService.cancelPendingOrdersForProduct(productId);
        String imageUrl = product.getImageUrl();
        product.setActive(false);
        product.setImageUrl(null);
        productRepo.save(product);
        deleteStoredImageIfUnreferenced(imageUrl);
    }

    private void deleteStoredImageIfUnreferenced(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || orderItemRepository.existsByImageUrlSnapshot(imageUrl)) {
            return;
        }
        deleteStoredImage(imageUrl);
    }

    private void deleteStoredImage(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/uploads/merch/")) {
            return;
        }
        String filename = imageUrl.substring("/uploads/merch/".length());
        if (filename.isBlank() || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return;
        }
        Path target = uploadRoot.resolve(filename).normalize();
        if (!target.startsWith(uploadRoot)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to remove product image", e);
        }
    }

    private String storeSanitizedImage(MultipartFile image) throws IOException {
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image too large (max 5 MB).");
        }

        byte[] uploadedBytes = image.getBytes();
        ImageFormat sourceFormat = detectImageFormat(uploadedBytes);
        if (sourceFormat == null) {
            throw new IllegalArgumentException("Unsupported image type. Use PNG, JPG, or WEBP.");
        }

        BufferedImage decoded;
        // Check metadata before allocating pixel buffers for compressed uploads.
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(uploadedBytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Unsupported image data.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                    || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IllegalArgumentException("Image dimensions are too large. Use at most 16 million pixels and 8192 pixels per side.");
                }
                decoded = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0) {
            throw new IllegalArgumentException("Unsupported image data. Use a valid PNG, JPG, or WEBP image.");
        }

        SanitizedImage sanitized = sanitizeImage(decoded, sourceFormat);
        Files.createDirectories(uploadRoot);

        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String filename = "product-" + System.currentTimeMillis() + "-" + uniqueSuffix + sanitized.extension();
        Files.write(uploadRoot.resolve(filename), sanitized.bytes());
        return "/uploads/merch/" + filename;
    }

    private SanitizedImage sanitizeImage(BufferedImage decoded, ImageFormat sourceFormat) throws IOException {
        boolean encodeAsJpeg = sourceFormat == ImageFormat.JPEG;
        int imageType = encodeAsJpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage sanitized = new BufferedImage(decoded.getWidth(), decoded.getHeight(), imageType);

        Graphics2D graphics = sanitized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(decoded, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String formatName = encodeAsJpeg ? "jpg" : "png";
        boolean written = ImageIO.write(sanitized, formatName, outputStream);
        if (!written) {
            throw new IllegalArgumentException("Unsupported image data. Use a valid PNG or JPG image.");
        }
        return new SanitizedImage(outputStream.toByteArray(), encodeAsJpeg ? ".jpg" : ".png");
    }

    private ImageFormat detectImageFormat(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if (hasPrefix(bytes, (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A)) {
            return ImageFormat.PNG;
        }
        if (hasPrefix(bytes, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF)) {
            return ImageFormat.JPEG;
        }
        if (hasPrefix(bytes, (byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F')
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return ImageFormat.WEBP;
        }
        return null;
    }

    private boolean hasPrefix(byte[] bytes, byte... prefix) {
        if (bytes == null || prefix == null || bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private enum ImageFormat {
        PNG,
        JPEG,
        WEBP
    }

    private record SanitizedImage(byte[] bytes, String extension) {
    }
}
