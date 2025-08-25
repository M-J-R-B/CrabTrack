package com.crabtrack.app.data.source.remote

import com.crabtrack.app.data.model.WaterReading
import com.crabtrack.app.data.source.TelemetrySource
import com.crabtrack.app.data.source.TelemetrySourceConfig
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseTelemetrySource @Inject constructor() : TelemetrySource {
    
    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance()
    }
    
    override fun stream(config: TelemetrySourceConfig): Flow<WaterReading> {
        return callbackFlow {
            val readingsRef = database.getReference("tanks")
                .child(config.tankId)
                .child("readings")
            
            val childEventListener = object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val reading = parseWaterReading(snapshot, config.tankId)
                    reading?.let { 
                        trySend(it)
                    }
                }
                
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    val reading = parseWaterReading(snapshot, config.tankId)
                    reading?.let { 
                        trySend(it)
                    }
                }
                
                override fun onChildRemoved(snapshot: DataSnapshot) {
                    // No action needed for removed readings
                }
                
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                    // No action needed for moved readings
                }
                
                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }
            
            readingsRef.addChildEventListener(childEventListener)
            
            awaitClose {
                readingsRef.removeEventListener(childEventListener)
            }
        }
    }
    
    private fun parseWaterReading(snapshot: DataSnapshot, tankId: String): WaterReading? {
        return try {
            val timestamp = snapshot.key?.toLongOrNull() ?: return null
            val data = snapshot.value as? Map<String, Any?> ?: return null
            
            WaterReading(
                tankId = tankId,
                timestampMs = timestamp,
                pH = (data["pH"] as? Number)?.toDouble() ?: return null,
                dissolvedOxygenMgL = (data["dissolvedOxygenMgL"] as? Number)?.toDouble() ?: return null,
                salinityPpt = (data["salinityPpt"] as? Number)?.toDouble() ?: return null,
                ammoniaMgL = (data["ammoniaMgL"] as? Number)?.toDouble() ?: return null,
                temperatureC = (data["temperatureC"] as? Number)?.toDouble() ?: return null,
                waterLevelCm = (data["waterLevelCm"] as? Number)?.toDouble() ?: return null
            )
        } catch (e: Exception) {
            null
        }
    }
}