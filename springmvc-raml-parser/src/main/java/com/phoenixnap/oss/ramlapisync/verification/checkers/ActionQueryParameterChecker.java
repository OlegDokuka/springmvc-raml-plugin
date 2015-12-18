package com.phoenixnap.oss.ramlapisync.verification.checkers;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.raml.model.Action;
import org.raml.model.ActionType;
import org.raml.model.MimeType;
import org.raml.model.parameter.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import com.phoenixnap.oss.ramlapisync.naming.Pair;
import com.phoenixnap.oss.ramlapisync.parser.ResourceParser;
import com.phoenixnap.oss.ramlapisync.verification.Issue;
import com.phoenixnap.oss.ramlapisync.verification.IssueLocation;
import com.phoenixnap.oss.ramlapisync.verification.IssueSeverity;
import com.phoenixnap.oss.ramlapisync.verification.IssueType;
import com.phoenixnap.oss.ramlapisync.verification.RamlActionVisitorCheck;

/**
 * A visitor that will be invoked when an action is identified
 * 
 * @author Kurt Paris
 * @since 0.0.2
 *
 */
public class ActionQueryParameterChecker implements RamlActionVisitorCheck {
	
	public static String QUERY_PARAMETER_MISSING = "Missing Query Parameter.";
	public static String QUERY_PARAMETER_FOUND_IN_FORM = "Missing Query Parameter but found in Form Parameters";
	public static String INCOMPATIBLE_TYPES = "Incompatible data types";
	public static String INCOMPATIBLE_VALIDATION = "Incompatible validation parameters";
	public static String REQUIRED_PARAM_HIDDEN = "Target requires parameter that is marked not required in reference.";

	/**
	 * Class Logger
	 */
	protected static final Logger logger = LoggerFactory.getLogger(ActionQueryParameterChecker.class);

	@Override
	public Pair<Set<Issue>, Set<Issue>> check(ActionType name, Action reference, Action target, IssueLocation location, IssueSeverity maxSeverity) {
		logger.debug("Checking Action " + name);
		Set<Issue> errors = new LinkedHashSet<>();
		Set<Issue> warnings = new LinkedHashSet<>();
		//Resource (and all children) missing - Log it
		Issue issue;
		if (reference.getQueryParameters() != null && !reference.getQueryParameters().isEmpty()) {
			for(Entry<String, QueryParameter> cParam : reference.getQueryParameters().entrySet()) {
				logger.debug("ActionQueryParameterChecker Checking param " + cParam.getKey());
				IssueSeverity targetSeverity = maxSeverity;
				if (target.getQueryParameters() == null 
						|| target.getQueryParameters().isEmpty()
						|| !target.getQueryParameters().containsKey(cParam.getKey())) {
					//we have a missing param, in case of required parameters this could break - upgrade severity
					
					if (!cParam.getValue().isRequired()) {
						targetSeverity = IssueSeverity.WARNING; //downgrade to warning for non required parameters
					} else {
						targetSeverity = IssueSeverity.ERROR;
					}
					
					//lets check if they are defined as form parameters since spring does not distinguish this. Do so only if we are checking the contract
					Map<String, MimeType> targetBody = target.getBody();
					if (location == IssueLocation.SOURCE 
							&& targetBody != null 
							&& targetBody.containsKey(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
							&& targetBody.get(MediaType.APPLICATION_FORM_URLENCODED_VALUE) != null
							&& targetBody.get(MediaType.APPLICATION_FORM_URLENCODED_VALUE).getFormParameters() != null
							&& targetBody.get(MediaType.APPLICATION_FORM_URLENCODED_VALUE).getFormParameters().containsKey(cParam.getKey())
							&& ResourceParser.doesActionTypeSupportRequestBody(reference.getType())) {
					   issue = new Issue(IssueSeverity.WARNING, location, IssueType.MISSING, QUERY_PARAMETER_FOUND_IN_FORM , reference.getResource(), reference, cParam.getKey());
					} else {
					   issue = new Issue(targetSeverity, location, IssueType.MISSING, QUERY_PARAMETER_MISSING , reference.getResource(), reference, cParam.getKey());
					}
					RamlCheckerResourceVisitorCoordinator.addIssue(errors, warnings, issue, issue.getDescription() + "  "+ cParam.getKey() + " in " + location.name());
				} else {
					QueryParameter referenceParameter = cParam.getValue();
					QueryParameter targetParameter = target.getQueryParameters().get(cParam.getKey());
					
					if (referenceParameter.isRequired() == false && targetParameter.isRequired()) {
						issue = new Issue(maxSeverity, location, IssueType.DIFFERENT, REQUIRED_PARAM_HIDDEN , reference.getResource(), reference, cParam.getKey());					
						RamlCheckerResourceVisitorCoordinator.addIssue(errors, warnings, issue, REQUIRED_PARAM_HIDDEN + " "+ cParam.getKey() + " in " + location.name());
					}
					
					if (referenceParameter.getType() != null && !referenceParameter.getType().equals(targetParameter.getType())) {
						issue = new Issue(IssueSeverity.WARNING, location, IssueType.DIFFERENT, INCOMPATIBLE_TYPES , reference.getResource(), reference, cParam.getKey());					
						RamlCheckerResourceVisitorCoordinator.addIssue(errors, warnings, issue, INCOMPATIBLE_TYPES + " "+ cParam.getKey() + " in " + location.name());
					}
					
					if ( (referenceParameter.getMinLength() != null && !referenceParameter.getMinLength().equals(targetParameter.getMinLength()))
							|| (referenceParameter.getMaxLength() != null && !referenceParameter.getMaxLength().equals(targetParameter.getMaxLength()))
							|| (referenceParameter.getMaximum() != null && !referenceParameter.getMaximum().equals(targetParameter.getMaximum()))
							|| (referenceParameter.getMinimum() != null && !referenceParameter.getMinimum().equals(targetParameter.getMinimum()))
							|| (referenceParameter.getPattern() != null && !referenceParameter.getPattern().equals(targetParameter.getPattern()))) {
						issue = new Issue(IssueSeverity.WARNING, location, IssueType.DIFFERENT, INCOMPATIBLE_VALIDATION , reference.getResource(), reference, cParam.getKey());					
						RamlCheckerResourceVisitorCoordinator.addIssue(errors, warnings, issue, INCOMPATIBLE_VALIDATION + " "+ cParam.getKey() + " in " + location.name());
					}
					
				}								
			}
		}
		return new Pair<>(warnings, errors);
	}
	
	
}
