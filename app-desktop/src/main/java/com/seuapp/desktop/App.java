package com.seuapp.desktop;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("/main-view.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 520, 360);
        scene.getStylesheets().add(getClass().getResource("./style.css").toExternalForm());
        stage.setTitle("Youtube Converter");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}