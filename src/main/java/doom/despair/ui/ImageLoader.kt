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
            val name = when (type) {
                ShipType.DESTROYER -> "Destroyer"
                ShipType.SUBMARINE -> "Sub"
                ShipType.AIRCRAFT_CARRIER -> "Carrier"
            }
            return loadImage(name, (type.shipLength()+1)-segment, layer)
        }
        fun getImageView(type: ShipType, segment: Int, rotation: Boolean): StackPane {
//            if (rotation)
            return StackPane(
                ImageView(loadImage("Background", 1)) .apply { },
                ImageView(loadImage(type, segment, 2)).apply { rotate = if (rotation) 0.0 else 90.0; opacity = 0.5 },
                ImageView(loadImage("Background", 3)),
                ImageView(loadImage(type, segment, 4)).apply { rotate = if (rotation) 0.0 else 90.0 }
            )
        }
    }
}