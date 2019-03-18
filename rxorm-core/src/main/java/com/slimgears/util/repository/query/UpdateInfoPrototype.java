package com.slimgears.util.repository.query;

import com.slimgears.util.autovalue.annotations.AutoValuePrototype;
import com.slimgears.util.autovalue.annotations.BuilderPrototype;
import com.slimgears.util.autovalue.annotations.HasMetaClassWithKey;

@AutoValuePrototype
public interface UpdateInfoPrototype<K, S extends HasMetaClassWithKey<K, S>> extends
        HasEntityMeta<K, S>,
        HasPredicate<S>,
        HasPropertyUpdates<S>,
        HasPagination {
}
