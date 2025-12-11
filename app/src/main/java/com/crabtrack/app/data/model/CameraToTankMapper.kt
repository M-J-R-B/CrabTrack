package com.crabtrack.app.data.model

/**
 * Maps camera IP addresses to tank IDs and vice versa.
 *
 * This mapper is used to associate molting alerts from specific cameras
 * with their corresponding tanks in the app.
 *
 * Supports both static mappings (hardcoded) and dynamic mappings from Firebase.
 * Dynamic mappings from /camera_mappings take precedence over static ones.
 */
object CameraToTankMapper {

    /**
     * Static mapping of camera IP addresses to tank IDs.
     * Used as fallback when no dynamic mapping exists.
     */
    private val staticCameraToTank: Map<String, String> = mapOf(
        "192.168.8.115" to "tank1"
    )

    /**
     * Dynamic mappings loaded from Firebase /camera_mappings.
     * These take precedence over static mappings.
     */
    private val dynamicCameraToTank: MutableMap<String, String> = mutableMapOf()

    /**
     * Updates dynamic mappings from Firebase.
     * Called by CameraMappingRepository when /camera_mappings data changes.
     *
     * @param mappings Map of camera IP to tank ID from Firebase
     */
    @Synchronized
    fun updateFromFirebase(mappings: Map<String, String>) {
        dynamicCameraToTank.clear()
        dynamicCameraToTank.putAll(mappings)
        android.util.Log.i("CameraToTankMapper", "Updated dynamic mappings: $mappings")
    }

    /**
     * Clears all dynamic mappings.
     */
    @Synchronized
    fun clearDynamicMappings() {
        dynamicCameraToTank.clear()
    }

    /**
     * Gets the tank ID for a given camera IP address.
     * Checks dynamic mappings first, then falls back to static mappings.
     *
     * @param cameraIp The camera IP address (e.g., "192.168.8.115")
     * @return The tank ID (e.g., "tank1") or null if not mapped
     */
    fun getTankId(cameraIp: String): String? {
        return dynamicCameraToTank[cameraIp] ?: staticCameraToTank[cameraIp]
    }

    /**
     * Gets the camera IP address for a given tank ID.
     * Checks dynamic mappings first, then falls back to static mappings.
     *
     * @param tankId The tank ID (e.g., "tank1")
     * @return The camera IP address or null if not mapped
     */
    fun getCameraIp(tankId: String): String? {
        // Check dynamic mappings first
        dynamicCameraToTank.entries.find { it.value == tankId }?.let { return it.key }
        // Fall back to static mappings
        staticCameraToTank.entries.find { it.value == tankId }?.let { return it.key }
        return null
    }

    /**
     * Gets the tank ID for a camera IP, or returns a default tank ID
     * based on the camera IP if not explicitly mapped.
     *
     * @param cameraIp The camera IP address
     * @return The tank ID, or a generated ID based on the IP
     */
    fun getTankIdOrDefault(cameraIp: String): String {
        return getTankId(cameraIp) ?: "tank_${cameraIp.replace(".", "_")}"
    }

    /**
     * Returns all known camera IPs (both static and dynamic).
     */
    fun getAllCameraIps(): Set<String> = staticCameraToTank.keys + dynamicCameraToTank.keys

    /**
     * Returns all known tank IDs (both static and dynamic).
     */
    fun getAllTankIds(): Set<String> = staticCameraToTank.values.toSet() + dynamicCameraToTank.values.toSet()

    /**
     * Checks if there are any dynamic mappings loaded.
     */
    fun hasDynamicMappings(): Boolean = dynamicCameraToTank.isNotEmpty()
}
