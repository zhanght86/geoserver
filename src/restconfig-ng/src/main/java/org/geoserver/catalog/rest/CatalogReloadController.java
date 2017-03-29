package org.geoserver.catalog.rest;

import org.geoserver.config.GeoServer;
import org.geoserver.rest.RestBaseController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalog reload controller
 */
@RestController
@RequestMapping(path = RestBaseController.ROOT_PATH)
public class CatalogReloadController extends GeoServerController {

    @Autowired
    public CatalogReloadController(GeoServer geoServer) {
        super(geoServer);
    }

    @RequestMapping(value = "/reload", method = {RequestMethod.POST, RequestMethod.PUT})
    public void reload() throws Exception {
        geoServer.reload();
    }
    @RequestMapping(value = "/reset", method = {RequestMethod.POST, RequestMethod.PUT})
    public void reset() {
        geoServer.reset();
    }
}
