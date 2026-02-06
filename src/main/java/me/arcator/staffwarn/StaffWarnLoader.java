package me.arcator.staffwarn;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

public class StaffWarnLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addDependency(
                new Dependency(new DefaultArtifact("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.0"), null)
        );
        resolver.addRepository(
                new RemoteRepository.Builder("central", "default",
                        MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build()
        );
        classpathBuilder.addLibrary(resolver);
    }
}
