package com.courseqa.documents.repository;
import com.courseqa.contracts.port.*;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import com.courseqa.common.FeatureNotImplementedException;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
public class JdbcCatalogReader implements CatalogReadPort {
    @Override public CatalogData load(UUID knowledgeBaseId) { throw new FeatureNotImplementedException("B","load"); }
}
