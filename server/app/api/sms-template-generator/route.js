import { NextResponse } from 'next/server';
import OpenAI from 'openai';

// Copy functions from f.mjs
const escapeRegex = (s = "") =>
  s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

function normalizeToSingleLine(str = "") {
  return str
    .replace(/\r?\n|\r/g, " ")  
    .replace(/\s+/g, " ")      
    .trim();
}

function buildSmartOtpRegexList(formats) {
  if (!formats || formats.length === 0) return [];
  if (!Array.isArray(formats)) formats = [formats];

  return formats
    .map((format) => {
      format = normalizeToSingleLine(format);
      if (!format.includes("{otp}")) return null;

      let pattern = escapeRegex(format);

      pattern = pattern.replace(/\\\{otp\\\}/gi, "(?<otp>[A-Za-z0-9\\-]{3,12})"); 
      pattern = pattern.replace(/\\\{date\\\}/gi, ".*");
      pattern = pattern.replace(/\\\{datetime\\\}/gi, ".*");
      pattern = pattern.replace(/\\\{time\\\}/gi, ".*");
      pattern = pattern.replace(/\\\{random\\\}/gi, "[A-Za-z0-9]{3,15}");
      pattern = pattern.replace(/\\\{.*?\\\}/gi, ".*");

      pattern = pattern
        .replace(/\\s+/g, "\\s*")
        .replace(/\\:/g, "[:：]?")
        .replace(/\\\./g, ".*");

      return new RegExp(pattern, "i");
    })
    .filter(Boolean);
}

const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY,
});

export async function POST(request) {
  try {
    const { smsText } = await request.json();

    if (!smsText) {
      return NextResponse.json({ error: 'SMS text is required' }, { status: 400 });
    }

    // Function to validate template against SMS
    const validateTemplate = (template, smsText) => {
      // First check if template has exactly one {otp} placeholder
      const otpCount = (template.match(/{otp}/gi) || []).length;
      if (otpCount === 0) {
        return { valid: false, reason: 'No {otp} placeholder found in template' };
      }
      if (otpCount > 1) {
        return { valid: false, reason: 'Template contains multiple {otp} placeholders. Only one {otp} placeholder is allowed per template.' };
      }
      
      const regexList = buildSmartOtpRegexList(template);
      if (regexList.length === 0) {
        return { valid: false, reason: 'Failed to build regex from template' };
      }
      
      const cleanMessage = normalizeToSingleLine(smsText);
      let otpFound = null;
      
      for (const regex of regexList) {
        try {
          const match = regex.exec(cleanMessage);
          otpFound = match?.groups?.otp || (match && match[1]) || null;
          if (otpFound) {
            break;
          }
        } catch (error) {
          return { valid: false, reason: `Regex error: ${error.message}` };
        }
      }
      
      if (!otpFound) {
        return { valid: false, reason: 'Could not extract OTP from SMS using the generated template' };
      }
      
      return { valid: true, otp: otpFound };
    };

    let template;
    let validationResult;
    let attempts = 0;
    const maxAttempts = 5;

    do {
      attempts++;
      
      const prompt = `
You are an expert SMS template generator. Convert the following SMS message into a template using the specific placeholder rules. The template must be compatible with our regex builder system and must successfully extract the OTP from the SMS.

CRITICAL RULES:
- You MUST use exactly ONE {otp} placeholder in the entire template
- Using multiple {otp} placeholders will cause regex errors and template failure
- If the SMS contains the OTP multiple times, choose the most appropriate one (usually the first occurrence)
- The template MUST match the exact structure of the SMS, including punctuation and spacing

Special placeholders supported:
- {otp} → (?<otp>[A-Za-z0-9\-]{3,12}) - Use for OTP digits/alphanumeric (ONLY ONE PER TEMPLATE)
- {date} / {datetime} / {time} → .* - Use for durations, dates, times
- {random} → [A-Za-z0-9]{3,15} - Use for purely alphanumeric random strings (no special chars like /+)
- {any} → .* - Use as fallback for anything else (links, tokens with special chars, repeated OTP references)

Additional Rules:
1. Always replace OTP digits/alpha with {otp} (but only once)
2. Durations → {time}
3. Random purely alphanumeric → {random}
4. Random with /, +, . → {any}
5. Keep static text exactly as-is
6. Spaces collapse into \\s*
7. : matches : or ：
8. . matches .*
9. # symbols should be preserved or handled with {any} if followed by numbers
10. @ symbols in URLs should be preserved or handled with {any}

Example 1:
SMS: "<#> 1770 is your OTP to login into Airtel Thanks app. Valid for 100 secs. Do not share with anyone. If this was not you click i.airtel.in/Contact N9BWuqauU1y"
Template: "<#> {otp} is your OTP to login into Airtel Thanks app. Valid for {time}. Do not share with anyone. If this was not you click {any} {random}"

Example 2 (SPECIFIC FOR REPEATED OTP):
SMS: "Dear customer, 5672 is the one Time Password from Vi. Expires in 3 min. Please do not share this OTP with anyone.OTP @www.myvi.in #5672"
Template: "Dear customer, {otp} is the one Time Password from Vi. Expires in {time}. Please do not share this OTP with anyone.OTP @www.myvi.in #{any}"

${validationResult && !validationResult.valid ? `Previous attempt failed: ${validationResult.reason}. 

ANALYSIS: The generated template did not work with our regex system. ${validationResult.reason.includes('multiple') ? 'The template contained multiple {otp} placeholders which is not allowed.' : 'The template could not extract the OTP from the SMS.'}

Please generate a new template that:
- Contains exactly ONE {otp} placeholder
- Matches the SMS structure precisely including punctuation and spacing
- Uses {any} for any repeated OTP references (like #5672 at the end)
- Uses {time} for durations like "3 min"
- Preserves all static text exactly as-is
- Handles @ symbols in URLs with {any}
- Handles # symbols followed by numbers with {any}

Focus on replacing only the first occurrence of the OTP with {otp} and use {any} for any subsequent occurrences or URL fragments.` : ''}

Now convert this SMS:
"${smsText}"

Return ONLY the template string, nothing else.
`;

      const completion = await openai.chat.completions.create({
        model: "gpt-3.5-turbo",
        messages: [
          {
            role: "system",
            content: "You are an expert SMS template generator. Return ONLY the template string, no explanations. Ensure the template contains exactly one {otp} placeholder and can extract the OTP from the SMS. Follow the user's instructions precisely, especially about using only one {otp} and handling repeated OTPs with {any}."
          },
          {
            role: "user",
            content: prompt
          }
        ],
        max_tokens: 1000,
        temperature: 0.1,
      });

      template = completion.choices[0]?.message?.content?.trim();
      
      if (!template) {
        console.error('OpenAI API completion response:', JSON.stringify(completion, null, 2));
        throw new Error(`Failed to generate template. API response: ${completion.choices?.length || 0} choices returned`);
      }

      // Clean the template - remove any surrounding quotes or unwanted characters
      template = template.replace(/^"+|"+$/g, '').trim();
      
      // Validate the generated template
      validationResult = validateTemplate(template, smsText);

    } while (!validationResult.valid && attempts < maxAttempts);

    if (!validationResult.valid) {
      let assistantExplanation = '';
      
      if (validationResult.reason.includes('multiple')) {
        assistantExplanation = `The AI generated a template with multiple {otp} placeholders, which is not allowed. This usually happens when the SMS contains the OTP multiple times.

Suggested solutions:
1. Try using only the first occurrence of the OTP in your SMS
2. Remove any repeated OTP references from the SMS text
3. If the OTP appears at the end (like #5672), consider omitting that part
4. Example: For "Dear customer, 5672 is your OTP... #5672", use "Dear customer, 5672 is your OTP..."`;

      } else if (validationResult.reason.includes('No {otp}')) {
        assistantExplanation = `The generated template doesn't contain any {otp} placeholder. This means the AI didn't identify an OTP in your SMS.

Suggested solutions:
1. Ensure your SMS contains a clear OTP (numbers or alphanumeric code)
2. Make sure the OTP is not obscured by special characters or formatting
3. Try rephrasing the SMS to highlight the OTP more clearly`;

      } else if (validationResult.reason.includes('Could not extract')) {
        assistantExplanation = `The template was generated but couldn't extract the OTP from your SMS. This might be due to complex formatting or unusual OTP patterns.

Suggested solutions:
1. Simplify the SMS text by removing unnecessary details
2. Ensure the OTP is in a standard format (4-6 digits or alphanumeric)
3. Avoid having the OTP multiple times in the message
4. Contact support with the exact SMS for manual template creation`;

      } else {
        assistantExplanation = `The template generation failed due to: ${validationResult.reason}

General advice:
- Keep SMS messages clear and concise
- Avoid multiple OTP references
- Use standard OTP formats (e.g., 4-6 digits)
- If issues persist, contact support with the exact SMS text for assistance`;
      }

      return NextResponse.json(
        { 
          error: `Failed to generate valid template after ${maxAttempts} attempts`,
          details: validationResult.reason,
          assistantExplanation,
          supportContact: "For immediate assistance, please contact support with the exact SMS text."
        },
        { status: 400 }
      );
    }

    return NextResponse.json({ 
      template,
      extractedOtp: validationResult.otp,
      success: true
    });
  } catch (error) {
    console.error('Template generation error:', error);
    return NextResponse.json(
      { 
        error: 'Failed to generate template',
        details: error.message,
        assistantExplanation: 'This could be due to OpenAI API issues, invalid SMS format, or internal server errors. Check your API key and try again.'
      },
      { status: 500 }
    );
  }
}
