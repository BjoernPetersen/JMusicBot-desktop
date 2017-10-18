package com.github.bjoernpetersen.deskbot.view;

import com.github.bjoernpetersen.deskbot.model.BotHolder;
import com.github.bjoernpetersen.jmusicbot.CancelException;
import com.github.bjoernpetersen.jmusicbot.InitStateWriter;
import com.github.bjoernpetersen.jmusicbot.InitializationException;
import com.github.bjoernpetersen.jmusicbot.Loggable;
import com.github.bjoernpetersen.jmusicbot.MusicBot;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PluginLoaderController implements InitStateWriter, Loggable {

  @Nonnull
  private final MusicBot.Builder builder;
  @Nonnull
  private final Stage stage;
  @Nonnull
  private final Alert alert;

  @Nullable
  private String pluginName;

  private final boolean showNotifications;

  private PluginLoaderController(@Nonnull Stage stage, @Nonnull MusicBot.Builder builder,
      boolean showNotifications) {
    this.showNotifications = showNotifications;
    this.builder = builder.initStateWriter(this);
    this.stage = stage;
    this.alert = new Alert(AlertType.INFORMATION);
    alert.setTitle("Initializing...");
    alert.getButtonTypes().clear();
    alert.getButtonTypes().add(ButtonType.CANCEL);
    alert.setResizable(true);
  }

  private void load() {
    stage.hide();
    AtomicReference<Throwable> exception = new AtomicReference<>();
    Thread initializer = new Thread(() -> {
      try {
        BotHolder.getInstance().set(builder.build());
      } catch (IllegalStateException e) {
        logSevere(e, "Could not create MusicBot");
        exception.set(e);
      } catch (CancelException e) {
        logFine("User aborted config");
      } catch (InitializationException e) {
        logInfo(e, "Could not initialize MusicBot");
        exception.set(e);
      } catch (InterruptedException e) {
        logFine("Interrupted during MusicBot initialization");
        exception.set(e);
      } catch (RuntimeException e) {
        logSevere(e, "Unknown error creating MusicBot");
        exception.set(e);
      }
      Platform.runLater(alert::hide);
    }, "InitializationThread");
    initializer.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        exception.set(e);
      }
    });
    initializer.start();
    alert.showAndWait();
    initializer.interrupt();
    if (!BotHolder.getInstance().hasValue()) {
      try {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(MainController.class.getResource("Main.fxml"));
        loader.load();
        Window controller = loader.getController();
        stage.show();
        controller.showOnStage(stage);

        Throwable thrown = exception.get();
        if (thrown != null && !(thrown instanceof InterruptedException)) {
          showError(thrown);
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      try {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(PlayerController.class.getResource("Player.fxml"));
        loader.load();
        PlayerController controller = loader.getController();
        controller.setShowNotifications(showNotifications);
        stage.show();
        controller.showOnStage(stage);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private void showError(Throwable e) {
    Alert alert = new Alert(AlertType.ERROR);
    alert.setTitle("Error during bot loading");
    alert.setHeaderText(e.getMessage());
    StringWriter stringWriter = new StringWriter();
    e.printStackTrace(new PrintWriter(stringWriter));
    alert.setContentText(stringWriter.toString());
    alert.setResizable(true);
    alert.initModality(Modality.APPLICATION_MODAL);
    alert.show();
    alert.setWidth(800);
    alert.setHeight(600);
  }

  @Override
  public void begin(@Nonnull String pluginName) {
    this.pluginName = pluginName;
    Platform.runLater(() -> {
      alert.setHeaderText("Loading " + pluginName);
      alert.setContentText("");
    });
  }

  @Override
  public void state(String s) {
    Platform.runLater(() -> alert.setContentText(s));
  }

  @Override
  public void warning(@Nonnull String s) {
    // TODO save warnings
    logWarning("Warning from plugin %s: %s", pluginName, s);
    Platform.runLater(() -> alert.setContentText(s));
  }

  static void load(Stage stage, MusicBot.Builder builder, boolean showNotifications) {
    new PluginLoaderController(stage, builder, showNotifications).load();
  }
}
