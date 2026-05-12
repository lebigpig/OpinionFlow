package com.lespider.opinionflow.repo

import com.lespider.opinionflow.domain.MilvusImportState
import org.springframework.data.jpa.repository.JpaRepository

interface MilvusImportStateRepository : JpaRepository<MilvusImportState, String>