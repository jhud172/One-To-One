package uk.ac.cf._5.group14.One_To_One.Merch;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import java.util.Locale;

@Controller
@RequestMapping("/admin/merch")
@RequiredArgsConstructor
public class AdminMerchController {
    private final MerchProductService productService;
    private final AuthHelper authHelper;
    private final AdminMerchService administration;

    @InitBinder("productForm")
    void bindProduct(WebDataBinder binder) {
        binder.setAllowedFields("name", "description", "price", "category", "stockQuantity", "originalStock", "active");
    }

    @GetMapping
    public ModelAndView list(@RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "") String state) {
        ModelAndView view = new ModelAndView("admin-views/merch/admin-list");
        String query = search.trim().toLowerCase(Locale.ROOT);
        var products = productService.getAllProducts();
        view.addObject("totalProducts", products.size());
        view.addObject("products", products.stream().filter(product -> query.isEmpty()
            || String.valueOf(product.getName()).toLowerCase(Locale.ROOT).contains(query)
            || String.valueOf(product.getCategory()).toLowerCase(Locale.ROOT).contains(query))
            .filter(product -> !"active".equals(state) && !"inactive".equals(state) || product.isActive() == "active".equals(state)).toList());
        view.addObject("search", search); view.addObject("selectedState", state);
        if (search.length() > 120 || (!state.isEmpty() && !"active".equals(state) && !"inactive".equals(state))) {
            view.setStatus(HttpStatus.BAD_REQUEST); view.addObject("errorMessage", "Use a search of up to 120 characters and a valid status.");
        }
        return view;
    }

    @GetMapping("/new")
    public ModelAndView newForm() { return formView(null, new MerchProductForm(), null); }

    @GetMapping("/{id}/edit")
    public ModelAndView editForm(@PathVariable Long id) {
        MerchProduct product = requireProduct(id);
        return formView(id, MerchProductForm.from(product), product.getImageUrl());
    }

    @PostMapping("/create")
    public ModelAndView create(@Valid @ModelAttribute("productForm") MerchProductForm form, BindingResult errors,
                               @RequestParam(value = "image", required = false) MultipartFile image, RedirectAttributes flash) {
        return save(null, form, errors, image, flash);
    }

    @PostMapping("/{id}/update")
    public ModelAndView update(@PathVariable Long id, @Valid @ModelAttribute("productForm") MerchProductForm form, BindingResult errors,
                               @RequestParam(value = "image", required = false) MultipartFile image, RedirectAttributes flash) {
        return save(id, form, errors, image, flash);
    }

    private ModelAndView save(Long id, MerchProductForm form, BindingResult errors, MultipartFile image, RedirectAttributes flash) {
        User admin = requireAdmin();
        MerchProduct existing = id != null ? requireProduct(id) : null;
        ModelAndView view = formView(id, form, existing != null ? existing.getImageUrl() : null);
        view.addAllObjects(errors.getModel());
        if (errors.hasErrors()) { view.setStatus(HttpStatus.BAD_REQUEST); return view; }
        try {
            administration.save(id, form, image, admin);
            flash.addFlashAttribute("successMessage", id == null ? "Product created successfully." : "Product updated successfully.");
            return new ModelAndView("redirect:/admin/merch");
        } catch (AdminMerchService.StockChangedException conflict) {
            view.setStatus(HttpStatus.CONFLICT);
            view.addObject("stockChanged", true); view.addObject("currentStock", conflict.getCurrentStock());
            form.setOriginalStock(conflict.getCurrentStock());
            return view;
        } catch (RuntimeException failure) {
            view.setStatus(HttpStatus.BAD_REQUEST);
            view.addObject("errorMessage", "Unable to save this product. Check its details and image, then try again.");
            return view;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        requireAdmin(); requireProduct(id);
        try {
            productService.deleteProduct(id);
            flash.addFlashAttribute("successMessage", "Product retired. Pending orders were cancelled; confirmed orders remain unchanged.");
        } catch (RuntimeException failure) {
            flash.addFlashAttribute("errorMessage", "Unable to retire this product. Pending payment reservations may still need reconciliation.");
        }
        return "redirect:/admin/merch";
    }

    private MerchProduct requireProduct(Long id) {
        return productService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private User requireAdmin() {
        User admin = authHelper.getAuthenticatedUser();
        if (admin == null || (admin.getRole() != Role.PLATFORM_ADMIN && admin.getRole() != Role.SUPER_ADMIN)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return admin;
    }

    private ModelAndView formView(Long id, MerchProductForm form, String imageUrl) {
        ModelAndView view = new ModelAndView("admin-views/merch/admin-form");
        view.addObject("productForm", form); view.addObject("editingId", id); view.addObject("currentImage", imageUrl);
        view.addObject("formAction", id == null ? "/admin/merch/create" : "/admin/merch/" + id + "/update");
        view.addObject("formTitle", id == null ? "Add product" : "Edit product");
        return view;
    }
}
