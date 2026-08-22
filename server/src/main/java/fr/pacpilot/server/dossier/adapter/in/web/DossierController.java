package fr.pacpilot.server.dossier.adapter.in.web;

import fr.pacpilot.server.dossier.adapter.in.web.DossierDtos.ClientResponse;
import fr.pacpilot.server.dossier.adapter.in.web.DossierDtos.CreateClientRequest;
import fr.pacpilot.server.dossier.adapter.in.web.DossierDtos.CreateSiteRequest;
import fr.pacpilot.server.dossier.adapter.in.web.DossierDtos.SiteResponse;
import fr.pacpilot.server.dossier.application.port.out.ClientRepository;
import fr.pacpilot.server.dossier.application.port.out.SiteRepository;
import fr.pacpilot.server.dossier.domain.Client;
import fr.pacpilot.server.dossier.domain.DwellingObservations;
import fr.pacpilot.server.dossier.domain.Site;
import fr.pacpilot.server.dossier.domain.SiteAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client and site records — the start of the pre-visit flow.
 *
 * <p><b>No driving port here, deliberately.</b> The other three contexts call {@code RunDimensioning},
 * {@code ResolveAids} and {@code BuildQuote}, because each has a decision to make. Dossier has none:
 * it records what the installer typed. Interposing a pass-through use case would add a layer that
 * holds nothing and hides nothing, which is a cost with no matching benefit.
 *
 * <p>The mapping between DTO and domain record lives here, and it is the only thing here.
 */
@RestController
@RequestMapping("/api")
class DossierController {

    private final ClientRepository clients;
    private final SiteRepository sites;
    private final Clock clock;

    DossierController(ClientRepository clients, SiteRepository sites, Clock clock) {
        this.clients = clients;
        this.sites = sites;
        this.clock = clock;
    }

    @PostMapping("/clients")
    ResponseEntity<ClientResponse> createClient(@RequestBody CreateClientRequest request) {
        Instant now = clock.instant();
        Client saved =
                clients.save(
                        new Client(
                                request.id(),
                                request.installerId(),
                                request.firstName(),
                                request.lastName(),
                                Optional.ofNullable(request.email()),
                                Optional.ofNullable(request.phone()),
                                now,
                                now,
                                Optional.empty()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        new ClientResponse(
                                saved.id(),
                                saved.installerId(),
                                saved.firstName(),
                                saved.lastName(),
                                saved.email().orElse(null),
                                saved.phone().orElse(null)));
    }

    @PostMapping("/sites")
    ResponseEntity<SiteResponse> createSite(@RequestBody CreateSiteRequest request) {
        Instant now = clock.instant();
        Site saved =
                sites.save(
                        new Site(
                                request.id(),
                                request.clientId(),
                                new SiteAddress(
                                        request.addressLine(),
                                        request.postcode(),
                                        request.commune(),
                                        request.departementCode(),
                                        Optional.ofNullable(request.latitude()),
                                        Optional.ofNullable(request.longitude())),
                                new DwellingObservations(
                                        request.surfaceCentiSquareMetres(),
                                        request.ceilingHeightCentimetres(),
                                        request.constructionPeriod(),
                                        request.insulationLevel(),
                                        request.ventilationType(),
                                        request.emitterType(),
                                        request.electricalSupplyKva()),
                                now,
                                now,
                                Optional.empty()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        new SiteResponse(
                                saved.id(),
                                saved.clientId(),
                                saved.address().addressLine(),
                                saved.address().postcode(),
                                saved.address().commune(),
                                saved.address().departementCode(),
                                saved.address().latitude().orElse(null),
                                saved.address().longitude().orElse(null),
                                saved.address().latitude().isPresent()));
    }
}
