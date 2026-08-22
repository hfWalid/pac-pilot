package fr.pacpilot.rulepacks;

import fr.pacpilot.core.aids.model.AidRulePack;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A filesystem-backed store — the local staging area, and what the tests run against.
 *
 * <p><b>Not the production store.</b> Production is S3-compatible object storage in an FR region
 * ({@code CLAUDE.md} §4.6) with immutability enforced by bucket policy, because the failure mode this
 * guarantee protects against is an operator with credentials rather than a bug in this class. What
 * this implementation does is enforce the same <i>contract</i>, so the pipeline and its tests
 * exercise the real refusals.
 *
 * <p>{@code CREATE_NEW} is doing the work: it fails if the file exists, so an overwrite is refused by
 * the filesystem rather than by a check this class could forget to make.
 */
public final class FilePackStore implements PackStore {

    private final Path directory;

    public FilePackStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public List<AidRulePack> published() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            List<AidRulePack> packs = new ArrayList<>();
            for (Path file : files.filter(path -> path.toString().endsWith(".pack")).toList()) {
                packs.add(PublishedPackFormat.read(Files.readString(file, StandardCharsets.UTF_8), file.toString()));
            }
            return List.copyOf(packs);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override
    public void publish(AidRulePack pack, String serialised) {
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(pack.getVersion().getValue() + ".pack");
            // CREATE_NEW rather than a prior existence check: the filesystem refuses the overwrite
            // atomically, where a check-then-write leaves a window.
            Files.writeString(
                    target, serialised, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (java.nio.file.FileAlreadyExistsException alreadyThere) {
            throw new PackValidationException(
                    "version '"
                            + pack.getVersion().getValue()
                            + "' is already published and packs are immutable. Correct a mistake by"
                            + " publishing a successor, never by overwriting.");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
