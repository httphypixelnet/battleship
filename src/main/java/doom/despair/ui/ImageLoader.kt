package doom.despair.ui

import doom.despair.ships.ShipType
import javafx.scene.image.Image;
import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane

class ImageLoader {
    companion object {
//        private val images = mutableMapOf<Pair<String, Int>, Image>()
        private val images = hashMapOf<String, Image>();
        fun loadImage(name: String, segment: Int, layer: Int): Image {
            return images.getOrPut(name+segment+layer) {
                try {
                    return@getOrPut Image("${name}_segment_${segment}-layer_${layer}.png",
                        32.0, 32.0, true, false, false)
                }
                catch (e: Error) {
                    throw RuntimeException("Failed to load image $name with segment:$segment layer:$layer", e)
                }

            }
        }
        fun loadImage(name: String, layer: Int): Image {
            return loadImage(name, 1, layer)
        }
        fun loadImage(type: ShipType, segment: Int, layer: Int): Image {
            return when (type) {
                ShipType.DESTROYER -> loadImage("Destroyer", segment, layer)
                ShipType.SUBMARINE -> loadImage("Sub", segment, layer)
                ShipType.AIRCRAFT_CARRIER -> loadImage("Carrier", segment, layer)
            }
        }
        fun getImageView(type: ShipType, segment: Int): StackPane {
            return StackPane(
                ImageView(loadImage("Background", 1)) /* .apply { fitWidth = 32.0; fitHeight = 32.0 } */,
                ImageView(loadImage(type, segment, 2))/* .apply { fitWidth = 32.0; fitHeight = 32.0 } */,
                ImageView(loadImage("Background", 3)) /* .apply { fitWidth = 32.0; fitHeight = 32.0 } */,
                ImageView(loadImage(type, segment, 4))/* .apply { fitWidth = 32.0; fitHeight = 32.0 } */
            )
        }
    }
}