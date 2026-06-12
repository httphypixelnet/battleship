package doom.despair.ui

import doom.despair.ships.ShipType
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.event.EventHandler
import javafx.scene.image.Image;
import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane
import javafx.util.Duration
import kotlin.math.max

class ImageLoader {
    companion object {
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
        fun loadImage(name: String, x: Double, y: Double): Image {
            return images.getOrPut(name+1+1) {
                try {
                    return@getOrPut Image("${name}.png", x, y, true, false, false)
                }
                catch (e: Error) {
                    throw RuntimeException("Failed to load image $name", e)
                }
            }
        }
        fun loadImage(name: String): Image {
            return loadImage(name, 32.0, 32.0)
        }
        fun loadImage(type: ShipType, segment: Int, layer: Int): Image {
            val name = when (type) {
                ShipType.DESTROYER -> "Destroyer"
                ShipType.SUBMARINE -> "Sub"
                ShipType.AIRCRAFT_CARRIER -> "Carrier"
            }
            return loadImage(name, (type.shipLength()+1)-segment, layer)
        }
        fun getImageView(type: ShipType, segment: Int, rotation: Boolean, hit: Boolean, op: Double): StackPane {
            val sp = StackPane(
                ImageView(loadImage("Background", 1)).apply { opacity = op },
                ImageView(loadImage(type, segment, 2)).apply { rotate = if (rotation) 0.0 else 90.0; opacity = max(op - .5, 0.0) },
                ImageView(loadImage("Background", 3)).apply { opacity = op },
                ImageView(loadImage(type, segment, 4)).apply { rotate = if (rotation) 0.0 else 90.0; opacity = op }
            )
            if (hit) sp.children.add(ImageView(loadImage("Hit", 5)))
            return sp
        }
        fun getImageView(type: ShipType, segment: Int, rotation: Boolean, hit: Boolean): StackPane {
            return getImageView(type, segment, rotation, hit, 1.0)
        }
        fun getAnimation(name: String, frames: Int, v: ImageView, next: Runnable): Timeline {
            val timeline = Timeline()
            for (i in 1..frames) {
                timeline.keyFrames.add(
                    KeyFrame(Duration.millis(i * 100.0), { v.image = loadImage(name, i, 5) })
                )
            }
            timeline.keyFrames.add(KeyFrame(Duration.millis(frames*100.0), { next.run() }))
            return timeline
        }
    }
}