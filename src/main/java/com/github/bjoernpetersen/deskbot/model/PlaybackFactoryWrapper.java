package com.github.bjoernpetersen.deskbot.model;

import com.github.bjoernpetersen.jmusicbot.NamedPlugin;
import com.github.bjoernpetersen.jmusicbot.config.Config;
import com.github.bjoernpetersen.jmusicbot.config.Config.Entry;
import com.github.bjoernpetersen.jmusicbot.playback.PlaybackFactory;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nonnull;

public class PlaybackFactoryWrapper implements NamedPlugin {

  @Nonnull
  private final PlaybackFactory playbackFactory;

  public PlaybackFactoryWrapper(@Nonnull PlaybackFactory playbackFactory) {
    this.playbackFactory = playbackFactory;
  }

  @Nonnull
  @Override
  public String getName() {
    return playbackFactory.getClass().getName();
  }

  @Nonnull
  @Override
  public String getReadableName() {
    return playbackFactory.getClass().getSimpleName();
  }

  @Nonnull
  @Override
  public List<? extends Entry> initializeConfigEntries(@Nonnull Config config) {
    return playbackFactory.initializeConfigEntries(config);
  }

  @Override
  public void destructConfigEntries() {
    playbackFactory.destructConfigEntries();
  }

  @Override
  public void close() throws IOException {
    playbackFactory.close();
  }

  @Nonnull
  public PlaybackFactory getWrapped() {
    return playbackFactory;
  }
}