package com.woocommerce.android.ui.products

import android.content.Intent
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.woocommerce.android.R
import com.woocommerce.android.model.Product
import com.woocommerce.android.ui.products.ProductNavigationTarget.ExitProduct
import com.woocommerce.android.ui.products.ProductNavigationTarget.ShareProduct
import com.woocommerce.android.ui.products.ProductNavigationTarget.ViewProductDescriptionEditor
import com.woocommerce.android.ui.products.ProductNavigationTarget.ViewProductImageChooser
import com.woocommerce.android.ui.products.ProductNavigationTarget.ViewProductImages
import com.woocommerce.android.ui.products.ProductNavigationTarget.ViewProductInventory
import com.woocommerce.android.ui.products.ProductNavigationTarget.ViewProductPricing
import com.woocommerce.android.ui.products.ProductNavigationTarget.ViewProductShipping
import com.woocommerce.android.ui.products.ProductNavigationTarget.ViewProductVariations
import com.woocommerce.android.util.FeatureFlag
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class that handles navigation logic for product related screens.
 * Currently injected to [BaseProductFragment]. Modify the [navigate] method when you want to add more logic
 * to navigate the Products detail/product sub detail screen.
 *
 * Note that a new data class in the [ProductNavigationTarget] must be added first
 */
@Singleton
class ProductNavigator @Inject constructor() {
    fun navigate(fragment: Fragment, target: ProductNavigationTarget) {
        when (target) {
            is ShareProduct -> {
                val shareIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_SUBJECT, target.title)
                    putExtra(Intent.EXTRA_TEXT, target.url)
                    type = "text/plain"
                }
                val title = fragment.resources.getText(R.string.product_share_dialog_title)
                fragment.startActivity(Intent.createChooser(shareIntent, title))
            }

            is ViewProductVariations -> {
                val action = ProductDetailFragmentDirections
                        .actionProductDetailFragmentToProductVariantsFragment(target.remoteId)
                fragment.findNavController().navigate(action)
            }

            is ViewProductDescriptionEditor -> {
                val action = ProductDetailFragmentDirections
                        .actionProductDetailFragmentToAztecEditorFragment(target.description, target.title)
                fragment.findNavController().navigate(action)
            }

            is ViewProductInventory -> {
                val action = ProductDetailFragmentDirections
                        .actionProductDetailFragmentToProductInventoryFragment(target.remoteId)
                fragment.findNavController().navigate(action)
            }

            is ViewProductPricing -> {
                val action = ProductDetailFragmentDirections
                        .actionProductDetailFragmentToProductPricingFragment(target.remoteId)
                fragment.findNavController().navigate(action)
            }

            is ViewProductShipping -> {
                val action = ProductDetailFragmentDirections
                        .actionProductDetailFragmentToProductShippingFragment(target.remoteId)
                fragment.findNavController().navigate(action)
            }

            is ViewProductImageChooser -> viewProductImageChooser(fragment, target.remoteId)

            is ViewProductImages -> {
                if (FeatureFlag.PRODUCT_RELEASE_M2.isEnabled(fragment.requireActivity())) {
                    viewProductImageChooser(fragment, target.product.remoteId)
                } else if (target.imageModel != null) {
                    viewProductImageViewer(fragment, target.imageModel, target.clickedImage)
                }
            }

            is ExitProduct -> fragment.findNavController().navigateUp()
        }
    }

    private fun viewProductImageChooser(fragment: Fragment, remoteId: Long) {
        val action = ProductDetailFragmentDirections
                .actionProductDetailFragmentToProductImagesFragment(remoteId)
        fragment.findNavController().navigate(action)
    }

    private fun viewProductImageViewer(fragment: Fragment, image: Product.Image, clickedView: WeakReference<View>) {
        clickedView.get()?.transitionName = fragment.getString(R.string.shared_element_transition_name)
        val action = ProductImageViewerFragmentDirections.actionGlobalProductImageViewerFragment(image.source, image.id)
        fragment.findNavController().navigate(action)
    }
}
