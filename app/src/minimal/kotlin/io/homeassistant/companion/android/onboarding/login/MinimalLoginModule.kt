package io.homeassistant.companion.android.onboarding.login

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MinimalLoginModule {

    @Binds
    abstract fun bindLoginRepository(impl: MinimalLoginRepositoryImpl): LoginRepository
}
