package com.arda.demo;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;

import java.sql.Statement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.Map;

public class DashboardController {
    @FXML private BorderPane rootPane;
    @FXML private Label timeLabel;
    @FXML private ProgressBar progressBar;
    @FXML private ChoiceBox<String> modeChoice;
    @FXML private Button startBtn;
    @FXML private Button pauseBtn;
    @FXML private Button restartBtn;
    @FXML private Label cycleLabel;
    @FXML private Button addTaskBtn;
    @FXML private TextField taskField;
    @FXML private ListView<taskObj> taskList=new ListView<taskObj>();
    @FXML private Button deleteTaskBtn;
    @FXML private TextField countField;

    private static class taskObj{
        private final int id;
        private final String name;
        private final int pomodoroCount;
        private int remainPomodoro;
        taskObj(int id,String name,int pomodoroCount ,int remainPomodoro){
            this.id=id;
            this.name=name;
            this.pomodoroCount=pomodoroCount;
            this.remainPomodoro=remainPomodoro;
        }

        public int getPomodoroCount() {
            return pomodoroCount;
        }

        public String getName() {
            return name;
        }

        public int getRemainPomodoro() {
            return remainPomodoro;
        }

        public void setRemainPomodoro(int remainPomodoro) {
            this.remainPomodoro = remainPomodoro;
        }

        @Override
        public String toString() {
            return getName() + " (" + getRemainPomodoro() + "/" + getPomodoroCount() + " sessions left)";
        }
    }
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Input Error");
        alert.setHeaderText("Task Name Can't Be Empty");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private Timeline timeline= new Timeline();
    private int totalSeconds;
    private int remainingSeconds;
    private final Map<String, Integer> modes= Map.of(
            "Pomodoro (25)",25,
            "Short Break (5)",5,
            "Long Break (15)",15
    );

    private int completedPomodoros=0;

    @FXML
    private void initialize(){
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:app.db");
                Statement stmt = conn.createStatement()) {

            String createSql = """
                CREATE TABLE IF NOT EXISTS tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    pomos_total INTEGER NOT NULL,
                    pomos_left INTEGER NOT NULL
                );
            """;

            stmt.execute(createSql);

            String selectSql = "SELECT id,name,pomos_total,pomos_left FROM tasks";
            try (PreparedStatement ps = conn.prepareStatement(selectSql);
                 ResultSet rs = ps.executeQuery()) {
                taskList.getItems().clear();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    int total = rs.getInt("pomos_total");
                    int left = rs.getInt("pomos_left");
                    taskObj task = new taskObj(id, name, total, left);
                    taskList.getItems().add(task);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        modeChoice.getItems().addAll(modes.keySet());
        modeChoice.setValue("Pomodoro (25)");
        applyMode();
        modeChoice.setOnAction(e-> {
            stopTimer();
            applyMode();
            updateUI();
        });
        setRunningStage(false);
    }
    private void applyMode(){
        setRunningStage(false);
        rootPane.getStyleClass().removeAll("pomodoro-mode","break-mode");
        int minute;
        if(modeChoice.getValue().equals("Pomodoro (25)")){
            minute = 25;
            timeLabel.setText("25:00");
            rootPane.getStyleClass().add("pomodoro-mode");
        }else if(modeChoice.getValue().equals("Short Break (5)")){
            minute = 5;
            timeLabel.setText("05:00");
            rootPane.getStyleClass().add("break-mode");
        }else {
            minute = 15;
            timeLabel.setText("15:00");
            rootPane.getStyleClass().add("break-mode");
        }
        remainingSeconds = minute * 60;
        totalSeconds = remainingSeconds;
        progressBar.setProgress(0);
    }
    @FXML
    private void onStart(){
        if(timeline.getStatus() == Animation.Status.RUNNING){
            return;
        }
        timeline= new Timeline(new KeyFrame(Duration.seconds(1),e->tick()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        setRunningStage(true);
    }

    private void stopTimer(){
        timeline.pause();
    }

    @FXML
    private void onPause(){
        if(timeline.getStatus() == Animation.Status.RUNNING){
            stopTimer();
            setRunningStage(false);
        }
    }

    @FXML
    private void onRestart(){
        stopTimer();
        remainingSeconds = totalSeconds;
        updateUI();
        setRunningStage(false);
    }

    private void tick(){
        if(remainingSeconds == 0){
            stopTimer();
            setRunningStage(false);
            if (modeChoice.getValue().equals("Pomodoro (25)")){
                if(taskList.getSelectionModel().getSelectedItem() != null){
                    int idx=taskList.getSelectionModel().getSelectedIndex();
                    taskObj selectedTask=taskList.getItems().get(idx);
                    selectedTask.setRemainPomodoro(selectedTask.getRemainPomodoro()-1);
                    int newLeft = selectedTask.getRemainPomodoro();
                    if(newLeft <= 0){
                        taskList.getItems().remove(selectedTask);
                        deleteTaskFromDb(selectedTask.id);
                    }else{
                        updateTaskLeft(selectedTask.id, newLeft);
                    }
                    taskList.refresh();
                }
                completedPomodoros++;
                if(completedPomodoros % 4 == 0){
                    modeChoice.setValue("Long Break (15)");
                }else{
                    modeChoice.setValue("Short Break (5)");
                    applyMode();
                }
                showCycle();
            }else if(modeChoice.getValue().equals("Long Break (15)")){
                modeChoice.setValue("Pomodoro (25)");
                completedPomodoros = 0;
                showCycle();
                applyMode();
            }else {
                modeChoice.setValue("Pomodoro (25)");
                applyMode();
            }
        }else{
            remainingSeconds--;
            updateUI();
        }
    }

    private void updateUI(){
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        String time;
        time=String.format("%02d:%02d", minutes, seconds);
        timeLabel.setText(time);
        progressBar.setProgress(1- (double) remainingSeconds / totalSeconds);
    }

    private void setRunningStage(boolean isRunning){
        if(!isRunning){
            startBtn.setDisable(false);
            restartBtn.setDisable(true);
            pauseBtn.setDisable(true);
        }else {
            startBtn.setDisable(true);
            restartBtn.setDisable(false);
            pauseBtn.setDisable(false);
        }
    }

    @FXML
    private void onAddTask(){
        String taskName = taskField.getText();
        if (taskName == null || taskName.isBlank()) {
            showError("Please enter a valid task name");
            return;
        }
        String countText = countField.getText();
        int pomoCount = 1;
        if (countText != null && !countText.isBlank()) {
            try {
                pomoCount = Integer.parseInt(countText);
            } catch (NumberFormatException e) {
                return;
            }
        }
        if (pomoCount <= 0) return;
        int id = insertTaskToDb(taskName, pomoCount,pomoCount);
        if (id == -1) return;
        taskObj task = new taskObj(id, taskName, pomoCount, pomoCount);
        taskList.getItems().add(task);
        taskField.setText("");
        countField.setText("1");
        taskList.getSelectionModel().select(task);
    }

    @FXML
    private  void onDeleteTask(){
        if(taskList.getSelectionModel().getSelectedItem() != null){
            int idx = taskList.getSelectionModel().getSelectedIndex();
            taskObj selectedTask=taskList.getItems().get(idx);
            taskList.getItems().remove(selectedTask);
            deleteTaskFromDb(selectedTask.id);
        }
    }

    private void showCycle(){
        if(completedPomodoros % 4 == 0){
            cycleLabel.setText("Cycle: 0/4");
        }else {
            cycleLabel.setText("Cycle: "+completedPomodoros+"/4");
        }
    }
    //helper to onAdd()
    private int insertTaskToDb(String name, int pomosTotal, int pomosLeft) {
        String sql =  "INSERT INTO tasks(name, pomos_total, pomos_left) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:app.db");
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.setInt(2, pomosTotal);
            ps.setInt(3, pomosLeft);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }
    //helper to tick()
    private void updateTaskLeft(int id, int newLeft) {
        String sql = "UPDATE tasks SET pomos_left = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:app.db");
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, newLeft);
            ps.setInt(2, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    //helper to onDelete()
    private void deleteTaskFromDb(int id) {
        String sql = "DELETE FROM tasks WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:app.db");
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
