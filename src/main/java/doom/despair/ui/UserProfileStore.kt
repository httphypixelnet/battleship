package doom.despair.ui

import com.google.gson.GsonBuilder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class UserProfile(
    val name: String = "Player",
    val wins: Int = 0,
    val losses: Int = 0,
    val lastServerAddress: String = "127.0.0.1:25567"
)
class UserProfileStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val profilePath: Path = Paths.get(
        System.getProperty("user.home"),
        ".battleship",
        "user-profile.json"
    )

    fun load(): UserProfile {
        return try {
            if (!Files.exists(profilePath)) {
                UserProfile()
            } else {
                Files.newBufferedReader(profilePath, StandardCharsets.UTF_8).use {
                    gson.fromJson(it, UserProfile::class.java) ?: UserProfile()
                }
            }
        } catch (_: Exception) {
            UserProfile()
        }
    }

    fun save(profile: UserProfile) {
        try {
            Files.createDirectories(profilePath.parent)
            Files.newBufferedWriter(profilePath, StandardCharsets.UTF_8).use {
                gson.toJson(profile, it)
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to save user profile at $profilePath", e)
        }
    }
}
