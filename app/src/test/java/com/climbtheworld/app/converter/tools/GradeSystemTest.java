package com.climbtheworld.app.converter.tools;

import org.junit.Test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GradeSystemTest {

	@Test
	public void exportGradesToCSV() {
		List<List<String>> result = new ArrayList<>();
		for (GradeSystem grade: GradeSystem.printableValues()) {
			List<String> temp = new ArrayList<>();
			temp.add(grade.key);
			temp.addAll(grade.getAllGrades());
			result.add(temp);
		}

		int i = 0;
		boolean exit;
		StringBuilder csvString = new StringBuilder();
		do {
			exit = true;
			for (List<String> grade: result) {
				StringBuilder line = new StringBuilder();
				if (i < grade.size()) {
					exit = false;
					line.append(grade.get(i));
				} else {
					line.append("");
				}

				if (grade != result.get(result.size() - 1)) {
					line.append(", ");
				} else {
					line.append("\n");
				}

				if (!exit) {
					csvString.append(line);
				}
			}
			i++;
		} while (!exit);

		System.out.println(csvString);

		File file = new File("gradeConverting.csv");

		try(FileOutputStream fos = new FileOutputStream(file);
		    BufferedOutputStream bos = new BufferedOutputStream(fos)) {
			//convert string to byte array
			byte[] bytes = csvString.toString().getBytes(StandardCharsets.UTF_8);
			//write byte array to file
			bos.write(bytes);
			bos.close();
			fos.close();
			System.out.print("Data written to file successfully.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}