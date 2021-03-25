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
public class CheckNumberWords extends Rule
{
	/**
	 * The pattern for finding numbers
	 */
	Pattern m_breakPattern = Pattern.compile("(-?\\d*\\.?\\d+)");
	/**
	* The sentence detector 
	*/
	SentenceDetectorME sdetector;
	public CheckNumberWords()
	{
		super("cyh:nw");
		try {
			InputStream is = new FileInputStream("Source/Core/dep/en-sent.bin");
			SentenceModel model = new SentenceModel(is);
		 	sdetector = new SentenceDetectorME(model);
		 	is.close();
		} catch (InvalidFormatException e) {
			System.out.println(e);
		} catch (IOException e) {
			System.out.println(e);
		}
	}

	@Override
	public List<Advice> evaluate(AnnotatedString s, AnnotatedString original)
	{
		List<Advice> out_list = new ArrayList<Advice>();
		List<String> lines = s.getLines();
		int env_level = 0;
		for (int line_cnt = 0; line_cnt < lines.size(); line_cnt++)
		{
			String line = lines.get(line_cnt).replaceAll("\\s+$", "");
			if (line.matches(".*\\\\begin\\s*\\{\\s*(equation|equation\\*|align|align\\*|table|tabular|verbatim|lstlisting|IEEEkeywords|figure|matrix|bmatrix|Bmatrix|pmatrix|vmatrix|Vmatrix|smallmatrix).*") || line.matches(".*\\\\\\[.*"))
			{
				env_level++;
			}
			if (env_level == 0)
			{
				String[] sentences = sdetector.sentDetect(line);
				int sentenceStartPos = 0;
				for (int i = 0; i < sentences.length; i++) {
					Matcher mat = m_breakPattern.matcher(sentences[i]);
					boolean violation_flag = true;
					Advice adv = null;
					while (mat.find())
					{
						String num_str = mat.group();
						if (num_str.contains(".")) {
							violation_flag = false;
							break;
						}
						int num = Integer.parseInt(num_str);
						if (num > 10 || num < 0) {
							violation_flag = false;
							break;
						}
						if (adv == null) {
							Position start_pos = s.getSourcePosition(new Position(line_cnt, sentenceStartPos + mat.start()));
							Position end_pos = s.getSourcePosition(new Position(line_cnt, sentenceStartPos + mat.start() + mat.group(0).length()));
							Range r = new Range(start_pos, end_pos);
							adv = new Advice(this, r, "You should use English word for integers from zero to ten.", original.getResourceName(), original.getLine(start_pos.getLine()), original.getOffset(start_pos));
						}
				
					}
					if (violation_flag && adv != null) {
						out_list.add(adv);
					}
					int moveLength = sentences[i].length() + (line.length() - line.trim().length());
					sentenceStartPos += moveLength;
					line = line.substring(moveLength);
				}
			}
			if (line.matches(".*\\\\end\\s*\\{\\s*(equation|equation\\*|align|align\\*|table|tabular|verbatim|lstlisting|IEEEkeywords|figure|matrix|bmatrix|Bmatrix|pmatrix|vmatrix|Vmatrix|smallmatrix).*") || line.matches(".*\\\\\\].*"))
			{
				env_level--;
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
