package sigma.console;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sigma.console.model.ResourceDefinition;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores resource definitions as per-resource YAML files in a Git
 * repository, one commit per published change. Git is the source of
 * truth and the audit trail: every publish records who changed what
 * and why.
 *
 * If the configured directory is not yet a Git repository, one is
 * initialized on first use (a fresh install works out of the box;
 * pointing at a clone of a shared config repo works the same way).
 * Pushing/PR flows arrive in a later phase.
 */
@Component
@ConditionalOnProperty(name = "sigma.console.edit.enabled", havingValue = "true")
public class GitConfigStore {

    private static final Logger logger = LoggerFactory.getLogger(GitConfigStore.class);

    private final Path resourcesDir;
    private final YAMLMapper yamlMapper = new YAMLMapper();

    public GitConfigStore(@Value("${sigma.console.resources-dir:config/resources}") String resourcesDir) {
        this.resourcesDir = Path.of(resourcesDir);
    }

    /**
     * Writes the definition as {name}.yaml and commits it.
     *
     * @param definition the resource being published
     * @param reason     the human explanation, becomes the commit body
     * @param author     who published, becomes the commit author name
     * @return the commit id
     */
    public synchronized String save(ResourceDefinition definition, String reason, String author) throws IOException {
        Files.createDirectories(resourcesDir);
        Path file = resourcesDir.resolve(definition.getName() + ".yaml");
        yamlMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), definition);

        try (Git git = openOrInit(resourcesDir.toFile())) {
            Path workTree = git.getRepository().getWorkTree().toPath().toAbsolutePath();
            String relative = workTree.relativize(file.toAbsolutePath()).toString().replace(File.separatorChar, '/');

            git.add().addFilepattern(relative).call();

            String safeAuthor = (author == null || author.isBlank()) ? "sigma-console" : author.trim();
            PersonIdent ident = new PersonIdent(safeAuthor, safeAuthor.replaceAll("\\s+", ".").toLowerCase() + "@sigma-console");
            RevCommit commit = git.commit()
                    .setMessage("console: publish resource '" + definition.getName() + "'\n\n" + reason)
                    .setAuthor(ident)
                    .setCommitter(ident)
                    // independent of any host-level signing configuration
                    .setSign(false)
                    .call();

            logger.info("Published resource '{}' as commit {} ({})",
                    definition.getName(), commit.abbreviate(9).name(), safeAuthor);
            return commit.getName();
        } catch (Exception e) {
            throw new IOException("Failed to commit resource '" + definition.getName() + "' to Git", e);
        }
    }

    /** Renders the YAML exactly as it would be committed, for previews. */
    public String toYaml(ResourceDefinition definition) {
        return yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(definition);
    }

    private Git openOrInit(File dir) throws Exception {
        try {
            return Git.open(dir);
        } catch (IOException notARepo) {
            logger.info("Initializing Git repository for resource definitions at {}", dir.getAbsolutePath());
            return Git.init().setDirectory(dir).call();
        }
    }
}
