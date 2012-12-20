package com.github.smreed.classloader;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Properties;

import static com.github.smreed.classloader.NotLogger.info;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class Bootstrap {

  private static final CharMatcher GAV_DELIMITER = CharMatcher.is(':');
  private static final Splitter GAV_SPLITTER = Splitter.on(GAV_DELIMITER);
  private static final Joiner GAV_JOINER = Joiner.on(':');

  private static MavenClassLoader.ClassLoaderBuilder classLoaderBuilder() {
    Optional<String> override = Settings.mavenRepoUrl();
    if (override.isPresent()) {
      info("Will load artifacts from %s", override);
      return MavenClassLoader.using(override.get());
    } else {
      return MavenClassLoader.usingCentralRepo();
    }
  }

  private static String resolveGav(String gav) {
    ImmutableList<String> tokens = ImmutableList.copyOf(GAV_SPLITTER.split(gav));

    checkArgument(tokens.size() > 1, "Require groupId:artifactId[:version]");
    checkArgument(tokens.size() < 4, "Require groupId:artifactId[:version]");

    if (tokens.size() > 2) {
      return gav;
    }

    Properties settings = Settings.loadBootstrapPropertiesUnchecked();

    if (settings.containsKey(gav)) {
      return GAV_JOINER.join(tokens.get(0), tokens.get(1), settings.getProperty(gav));
    } else {
      return GAV_JOINER.join(tokens.get(0), tokens.get(1), "[0,)");
    }
  }

  public static void main(String[] args) throws Exception {
    args = checkNotNull(args);
    checkArgument(args.length >= 2, "Must specify groupId:artifactId[:version] and classname!");

    String gav = resolveGav(args[0]);

    info("Requested %s, will load artifact and dependencies for %s.", args[0], gav);

    URLClassLoader loader = classLoaderBuilder().forGAV(gav);

    Class<?> mainClass = loader.loadClass(args[1]);

    Thread.currentThread().setContextClassLoader(loader);

    Method mainMethod = mainClass.getMethod("main", String[].class);

    Iterable<String> mainArgs = Iterables.skip(ImmutableList.copyOf(args), 2);
    mainMethod.invoke(null, (Object) Iterables.toArray(mainArgs, String.class));
  }
}
