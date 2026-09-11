package com.example.data

import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val dao: AutoTapDao) {

    val allProfiles: Flow<List<Profile>> = dao.getAllProfilesFlow()

    suspend fun getAllProfiles(): List<Profile> {
        return dao.getAllProfiles()
    }

    fun getProfileWithTargets(profileId: Long): Flow<ProfileWithTargets?> {
        return dao.getProfileWithTargetsFlow(profileId)
    }

    suspend fun getProfileWithTargetsDirect(profileId: Long): ProfileWithTargets? {
        return dao.getProfileWithTargets(profileId)
    }

    suspend fun insertProfile(profile: Profile): Long {
        return dao.insertProfile(profile)
    }

    suspend fun updateProfile(profile: Profile) {
        dao.updateProfile(profile)
    }

    suspend fun deleteProfile(profile: Profile) {
        dao.deleteProfile(profile)
    }

    suspend fun insertTargetImage(target: TargetImage): Long {
        return dao.insertTargetImage(target)
    }

    suspend fun updateTargetImage(target: TargetImage) {
        dao.updateTargetImage(target)
    }

    suspend fun deleteTargetImage(target: TargetImage) {
        dao.deleteTargetImage(target)
    }
}
