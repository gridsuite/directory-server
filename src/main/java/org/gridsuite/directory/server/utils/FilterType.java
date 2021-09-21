/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.utils;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
/* For now we only need this at filter creation and there is 2 possibilities:
*  - we create a script so the type is SCRIPT
*  - we create a filter in which case it is a type line because - FOR NOW - when we create a filter
*  we create a line filter by default.
*  This behaviour is not what we aim for and it'll probably change soon enough.
* */
public enum FilterType {
    SCRIPT(""),
    LINE("lines");

    private final String collectionName;

    FilterType(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getCollectionName() {
        return collectionName;
    }
}
