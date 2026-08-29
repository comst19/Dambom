package com.comst19.dambom.core.navigation.contract

import androidx.navigation3.runtime.NavKey

/** 앱의 모든 Navigation 3 destination key가 구현하는 공통 계약입니다. */
interface AppNavKey : NavKey

/** 독립 back stack의 root로 사용할 key가 구현하는 타입입니다. */
interface TopLevelNavKey : AppNavKey
