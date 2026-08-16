package com.careerops.backend.collector;

import com.careerops.backend.collector.alio.AlioApiException;
import com.careerops.backend.collector.alio.AlioCollectorService;
import com.careerops.backend.collector.alio.AlioCollectionInProgressException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/collect")
public class CollectController {

    private final AlioCollectorService alioCollectorService;

    public CollectController(AlioCollectorService alioCollectorService) {
        this.alioCollectorService = alioCollectorService;
    }

    @PostMapping("/{source}")
    public CollectResult collect(
            @PathVariable String source,
            @RequestParam(defaultValue = "50") int numOfRows
    ) {
        if (!"alio".equalsIgnoreCase(source)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported source");
        }
        try {
            return alioCollectorService.collect(numOfRows);
        } catch (AlioCollectionInProgressException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ALIO collection already in progress");
        } catch (AlioApiException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ALIO API request failed", exception);
        }
    }
}
