/*
    TeXtidote, a linter for LaTeX documents
    Copyright (C) 2018  Sylvain Hallé

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package ca.uqac.lif.textidote.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import ca.uqac.lif.textidote.Advice;
import ca.uqac.lif.textidote.Rule;
import ca.uqac.lif.textidote.as.AnnotatedString;
import ca.uqac.lif.textidote.as.Position;
import ca.uqac.lif.textidote.as.Range;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

/**
 * Checks the number follow the right format.
 * 
 * @author Chen Yuheng
 *
 */
public class CheckTabularNumber extends Rule
{
	/**
	 * The pattern for finding numbers
	 */
	Pattern m_breakPattern = Pattern.compile("(-?\\d*\\.?\\d+)");

	//Pattern m_columnp;
	Pattern m_columnp = Pattern.compile(".*\\\\begin\\s*\\{\\s*tabular\\}\\{(.*?)\\n");
	Pattern m_dd = Pattern.compile("(.*?)\\\\\\\\(.*?)");

	/**
	* The sentence detector 
	*/
	public CheckTabularNumber()
	{
		super("cyh:nw");
	}

	public static List<String> extractSpec(String spec) {
		List<String> out_list = new ArrayList<String>();
		for (int i = 0; i < spec.length(); i++) {
			char curr = spec.charAt(i);
			if (Character.isWhitespace(curr)) {
				continue;
			}
			if (curr == '{') {
				int start_pos = i;
				while (spec.charAt(i++) != '}');
				out_list.add(spec.substring(start_pos, i--));
				continue;
			}
			if (curr == '}') {
				break;
			}
			out_list.add(curr + "");
		}
		return out_list;
	}

	public static int countNewRowOccurences(String line) {
		int count = 0;
		for (int i = 0; i < line.length(); i++) {
			if (line.charAt(i) == '\\') {
				if (line.charAt(i + 1) == '\\') {
					count++;
					i++;
				}
			}
		}
		return count;
	}

	@Override
	public List<Advice> evaluate(AnnotatedString s, AnnotatedString original)
	{
		List<Advice> out_list = new ArrayList<Advice>();
		List<String> lines = s.getLines();
		boolean in_table = false;
		int row_count = 0;
		int column_num = 0;
		int[] number_count = new int[0];
		List<String> spec_list = new ArrayList<String>();
		List<Character> colum_spec_list = new ArrayList<Character>();
		Position start_pos = null;
		Position end_pos = null;
		for (int line_cnt = 0; line_cnt < lines.size(); line_cnt++)
		{
			String line = lines.get(line_cnt).replaceAll("\\s+$", "");
			if (line.matches(".*\\\\end\\s*\\{\\s*(tabular).*"))
			{
				if (!in_table) { // no start of tabular yet, ignore it
					continue;
				}
				boolean advice_flag = false;
				for (int i = 0; i < column_num; i++) {
					double number_ratio = 1.0 * number_count[i] / row_count;
					if (number_ratio > 0.5) {
						if (colum_spec_list.get(i) != 'S') {
							advice_flag = true;	
						}
					}
				}
				if (advice_flag) {
					Range r = new Range(start_pos, end_pos);
					out_list.add(new Advice(this, r, "You shold align number columns by decimal point.", original.getResourceName(), original.getLine(start_pos.getLine()), original.getOffset(start_pos)));	
				}
				in_table = false;
				column_num = 0;
				number_count = new int[0];
				spec_list = new ArrayList<String>();
				colum_spec_list = new ArrayList<Character>();
			}
			if (in_table) {
				Matcher mat = m_dd.matcher(line);
				if (mat.find()) {
					if (countNewRowOccurences(line) > 1) {
						Position start_pos_t = s.getSourcePosition(new Position(line_cnt, mat.end(1)));
						Position end_pos_t = s.getSourcePosition(new Position(line_cnt, mat.start(2)));
						Range r = new Range(start_pos_t, end_pos_t);
						out_list.add(new Advice(this, r, "You should start a new row at most once in one row in source file.", original.getResourceName(), original.getLine(start_pos_t.getLine()), original.getOffset(start_pos_t)));
						continue;
					}
					line = mat.group(1);
					if (line.contains("\\multicolumn")) {
						// complex situation, not considered yet
						continue;
					}
					line = String.join("$", line.split("\\\\&"));
					String[] cells = line.split("&");
					if (cells.length != column_num) {
						Position start_pos_t = s.getSourcePosition(new Position(line_cnt, 0));
						Position end_pos_t = s.getSourcePosition(new Position(line_cnt, line.length() - 1));
						Range r = new Range(start_pos_t, end_pos_t);
						out_list.add(new Advice(this, r, "The number of columns should be equal to the coloum description.", original.getResourceName(), original.getLine(start_pos_t.getLine()), original.getOffset(start_pos_t)));
						continue;
					}
					for (int i = 0; i < cells.length; i++) {
						try {
							Double.parseDouble(cells[i]);
							number_count[i]++;
						} catch (NumberFormatException e) {
							//
						}
					}
					row_count++;
				}
			}
			if (line.matches(".*\\\\begin\\s*\\{\\s*(tabular).*"))
			{
				in_table = true;
				Matcher mat = m_columnp.matcher(line + "\n");
				mat.find();
				String columnPattern = mat.group(1);
				start_pos = s.getSourcePosition(new Position(line_cnt, mat.start(1)));
				end_pos = s.getSourcePosition(new Position(line_cnt, mat.end(1) - 1));
				spec_list = extractSpec(columnPattern);
				for (int i = 0; i < spec_list.size(); i++) {
					String curr = spec_list.get(i);
					if (!curr.equals("|") && curr.length() == 1) {
						column_num++;
						colum_spec_list.add(curr.charAt(0));
					}
				}
				number_count = new int[column_num];
			}


		}
		return out_list;
	}
	
	@Override
	public String getDescription()
	{
		return "Don't use Arabic numbers for integers from zero to ten.";
	}
}
