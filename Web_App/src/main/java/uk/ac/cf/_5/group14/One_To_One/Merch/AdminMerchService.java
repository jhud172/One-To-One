package uk.ac.cf._5.group14.One_To_One.Merch;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.Role;

@Service
@Validated
@RequiredArgsConstructor
public class AdminMerchService {
    private final MerchProductRepository products;
    private final MerchProductService productService;
    private final EntityManager entities;

    @Transactional
    public MerchProduct save(Long id, @Valid MerchProductForm form, MultipartFile image, User admin) {
        if (admin == null || (admin.getRole() != Role.PLATFORM_ADMIN && admin.getRole() != Role.SUPER_ADMIN)) {
            throw new IllegalArgumentException("Access denied");
        }
        MerchProduct product;
        if (id == null) {
            product = new MerchProduct();
            product.setCreatedBy(admin.getId());
        } else {
            product = products.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Product not found"));
            // A purchase may have updated stock since an earlier request-context read.
            entities.refresh(product);
            if (form.getOriginalStock() == null || form.getOriginalStock() != product.getStockQuantity()) {
                throw new StockChangedException(product.getStockQuantity());
            }
        }
        product.setName(form.getName().trim());
        product.setDescription(form.getDescription() != null ? form.getDescription().trim() : null);
        product.setPrice(form.getPrice());
        product.setCategory(form.getCategory() != null && !form.getCategory().isBlank() ? form.getCategory().trim() : null);
        product.setStockQuantity(form.getStockQuantity());
        product.setActive(form.isActive());
        return productService.saveWithImage(product, image);
    }

    public static class StockChangedException extends IllegalStateException {
        private final int currentStock;
        public StockChangedException(int currentStock) { super("Stock changed while this product was being edited"); this.currentStock = currentStock; }
        public int getCurrentStock() { return currentStock; }
    }
}
