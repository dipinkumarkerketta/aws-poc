package com.qc.controller;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.qc.dto.APIErrorDTO;
import com.qc.dto.ExistingUserDTO;
import com.qc.dto.OTPRequiredDTO;
import com.qc.preissuance.dao.PreissuanceDao;
import com.qc.preissuance.dto.BackFlowServiceDTO;
import com.qc.preissuance.dto.BenefitNames;
import com.qc.preissuance.dto.LoginDto;
import com.qc.preissuance.dto.MProBuyerDetailsDTO;
import com.qc.preissuance.dto.MproBuyerResponseObj;
import com.qc.preissuance.dto.PiSellerTxnDto;
import com.qc.preissuance.dto.ProductType;
import com.qc.preissuance.dto.SellerDto;
import com.qc.preissuance.dto.SlideMproQuestionDto;
import com.qc.preissuance.dto.SlideQuestionDto;
import com.qc.preissuance.service.PreissuanceService;
import com.qc.preissuance.serviceImpl.AESEncryptor;
import com.qc.utils.AesUtil;
import com.qc.utils.Commons;
import com.qc.utils.Constants;
import com.qc.utils.StringUtility;
import com.qc.utils.UniqueId;

@Controller
@PropertySource({ "classpath:application.properties" })
public class PreissuanceControllerMVC {
	private static Logger logger = LogManager.getLogger(PreissuanceControllerMVC.class);

	private String preissuancejsp = "/preissuance/";
	private String loginPage = Constants.LOGIN;
	SimpleDateFormat formatter = new SimpleDateFormat("dd:MM:yyyy HH:mm:ss");

	@Autowired
	HttpSession session;

	@Autowired
	HttpServletRequest request;

	@Autowired
	ServletContext context;

	@Autowired
	Environment env;

	@Autowired
	PreissuanceService preissuanceService;

	@Autowired
	PreissuanceDao preissuanceDao;

	String newJspCheck = "null";

	@RequestMapping("/")
	public String index() {
		logger.info("login page showing : removing all attributes from session");
		session.removeAttribute(Constants.LOGIN);
		session.removeAttribute(Constants.TXNID);
		session.removeAttribute(Constants.SLIDEQUESTIONDTOS);
		session.removeAttribute(Constants.SELLERDTO);
		session.removeAttribute(Constants.SELLERRESPONSELIST);
		return preissuancejsp + loginPage;
	}

	@RequestMapping(value = Constants.LOGIN, method = { RequestMethod.GET, RequestMethod.POST })
	public String login() {
		logger.info("login called : removing all attributes from session");
		session.removeAttribute(Constants.LOGIN);
		session.removeAttribute(Constants.TXNID);
		session.removeAttribute(Constants.SLIDEQUESTIONDTOS);
		session.removeAttribute(Constants.SELLERDTO);
		session.removeAttribute(Constants.SELLERRESPONSELIST);
		return preissuancejsp + loginPage;
	}

	@RequestMapping(value = "/{*}", method = { RequestMethod.GET })
	public String errors(HttpServletRequest req, Model model) {
		logger.info("errors called : removing all attributes from session");
		session.removeAttribute(Constants.LOGIN);
		session.removeAttribute(Constants.TXNID);
		session.removeAttribute(Constants.SLIDEQUESTIONDTOS);
		session.removeAttribute(Constants.SELLERDTO);
		session.removeAttribute(Constants.SELLERRESPONSELIST);
		return preissuancejsp + Constants.ERRORPAGE;
	}

	@RequestMapping(value = "step-1", method = { RequestMethod.POST })
	public String stepH1(@ModelAttribute("loginDto") LoginDto loginDto, Model model) {
		try {
			ThreadContext.push("step-1_" + UniqueId.getUniqueId());
			SellerDto sellerDto1 = (SellerDto) session.getAttribute(Constants.LOGIN);
			List<String> sellerResponseList = (List<String>) session.getAttribute(Constants.SELLERRESPONSELIST);
			if (sellerDto1 != null && !"".equalsIgnoreCase(sellerDto1.getTxnId())) {
				logger.info("going backword on FName Page");
				return preissuancejsp + "step-1";
			} else {
				logger.info("Login : Start");
				AesUtil.decryptText(loginDto);
				String response = preissuanceService.sellerLogin(loginDto);
				Map loginResponse = new Commons().getGsonData(response);
				if (loginResponse != null && loginResponse.containsKey("response")) {
					Map responseData = (Map) ((Map) loginResponse.get("response")).get("responseData");
					if (responseData != null && responseData.containsKey("loginStatus")) {
						if ("SUCCESS".equalsIgnoreCase(responseData.get("loginStatus").toString())) {
							String txnId = preissuanceService.getSequnceValue("PI_TXN_SEQ");
							logger.info("TxnID : " + txnId);
							SellerDto sellerDto = new SellerDto();
							sellerDto.setAgentCode(responseData.get("agentCode").toString());
							sellerDto.setAgentMobileNumber(responseData.get("agentMobileNumber").toString());
							sellerDto.setTxnId(txnId);
							sellerDto.setLoginStatus(responseData.get("loginStatus").toString());
							sellerDto.setLoginInfo(responseData.get("loginInfo").toString());
							sellerDto.setGoCode(responseData.get("goCode").toString());
							sellerDto.setChannelName(responseData.get("channelName").toString());
							sellerDto.setAgentName(responseData.get("fullName").toString());

							session.setAttribute(Constants.LOGIN, sellerDto);
							session.setAttribute(Constants.SELLERRESPONSELIST, sellerResponseList);
							logger.info("Login Success Going Forrward on FName Page");
							return preissuancejsp + "step-1";
						} else {
							model.addAttribute("ERROR", "Please enter valid credentials");
							logger.info("Login : Failed : loginStatus : " + response);
							return preissuancejsp + Constants.LOGIN;
						}
					} else {
						logger.info("Login : Failed : Unable to fetch data from Login json : " + response);
					}
				} else {
					logger.info("Login : Failed : " + response);
				}
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured in stepH1 :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("Un Authorized Access or Error from Login API : Throwing to login");
		model.addAttribute("ERROR", "Please enter valid credentials");
		return preissuancejsp + Constants.LOGIN;
	}

	@RequestMapping(value = "step-1-1", method = { RequestMethod.POST })
	public String stepH1_1(@ModelAttribute("loginDto") LoginDto loginDto, Model model, HttpServletRequest request) {
		List<String> sellerResponseList = null;
		try {
			SellerDto sellerDto = (SellerDto) session.getAttribute(Constants.LOGIN);
			sellerResponseList = (List<String>) session.getAttribute(Constants.SELLERRESPONSELIST);
			if (sellerResponseList == null || sellerResponseList.isEmpty()) {
				sellerResponseList = new ArrayList<String>();
			}
			if (sellerDto != null && !"".equalsIgnoreCase(sellerDto.getTxnId())) {
				ThreadContext.push("step-1-1 : " + sellerDto.getTxnId());
				if (loginDto.getAnswer() != null && !"".equals(loginDto.getAnswer())) {
					logger.info(loginDto.getAnswer());
					sellerDto.setQuFName(loginDto.getQuestion());
					sellerDto.setCustFName(loginDto.getAnswer());
					sellerResponseList.add(loginDto.getQuestion());
					sellerResponseList.add(loginDto.getAnswer());
					sellerResponseList.add(formatter.format(new Date()));
					session.setAttribute(Constants.SELLERRESPONSELIST, sellerResponseList);
					session.setAttribute(Constants.LOGIN, sellerDto);
				}
				return preissuancejsp + "step-1-1";
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured in stepH1_1 :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info(Constants.UNAUTHORIZEDACCESS);
		return preissuancejsp + Constants.LOGIN;
	}

	@RequestMapping(value = "step-1-2", method = { RequestMethod.POST })
	public String stepH1_2(@ModelAttribute("loginDto") LoginDto loginDto, Model model) {
		try {
			SellerDto sellerDto = (SellerDto) session.getAttribute(Constants.LOGIN);
			List<String> sellerResponseList = (List<String>) session.getAttribute(Constants.SELLERRESPONSELIST);
			if (sellerDto != null && !"".equalsIgnoreCase(sellerDto.getTxnId())) {
				ThreadContext.push("step-1-2 : " + sellerDto.getTxnId());
				if (loginDto.getAnswer() != null && !"".equals(loginDto.getAnswer())) {
					logger.info(loginDto.getAnswer());
					sellerDto.setQuMName(loginDto.getQuestion());
					sellerDto.setCustMName(loginDto.getAnswer());
					sellerResponseList.add(loginDto.getQuestion());
					sellerResponseList.add(loginDto.getAnswer());
					sellerResponseList.add(formatter.format(new Date()));
					session.setAttribute(Constants.SELLERRESPONSELIST, sellerResponseList);
					session.setAttribute(Constants.LOGIN, sellerDto);
				}
				logger.info("Returning from controller :: stepH1_2");
				return preissuancejsp + "step-1-2";
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured inside stepH1_2 :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info(Constants.UNAUTHORIZEDACCESS);
		return preissuancejsp + Constants.LOGIN;

	}

	@RequestMapping(value = "step-2", method = { RequestMethod.POST })
	public String stepH2(@ModelAttribute("loginDto") LoginDto loginDto, Model model) {
		try {
			SellerDto sellerDto = (SellerDto) session.getAttribute(Constants.LOGIN);
			List<String> sellerResponseList = (List<String>) session.getAttribute(Constants.SELLERRESPONSELIST);
			if (sellerDto != null && !"".equalsIgnoreCase(sellerDto.getTxnId())) {
				ThreadContext.push("step-2 : " + sellerDto.getTxnId());
				if (loginDto.getAnswer() != null && !"".equals(loginDto.getAnswer())) {
					logger.info(loginDto.getAnswer());
					sellerDto.setQuLName(loginDto.getQuestion());
					sellerDto.setCustLName(loginDto.getAnswer());
					sellerResponseList.add(loginDto.getQuestion());
					sellerResponseList.add(loginDto.getAnswer());
					sellerResponseList.add(formatter.format(new Date()));
					session.setAttribute(Constants.SELLERRESPONSELIST, sellerResponseList);
					session.setAttribute(Constants.LOGIN, sellerDto);
				}
				logger.info("Returning from controller :: stepH2");
				return preissuancejsp + "step-2";
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured inside stepH2 :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info(Constants.UNAUTHORIZEDACCESS);
		return preissuancejsp + Constants.LOGIN;
	}

	@RequestMapping(value = "step-3", method = { RequestMethod.POST })
	public String stepH3(@ModelAttribute("loginDto") LoginDto loginDto, Model model) {
		try {
			SellerDto sellerDto = (SellerDto) session.getAttribute(Constants.LOGIN);
			List<String> sellerResponseList = (List<String>) session.getAttribute(Constants.SELLERRESPONSELIST);
			if (sellerDto != null && !"".equalsIgnoreCase(sellerDto.getTxnId())) {
				ThreadContext.push("step-3 : " + sellerDto.getTxnId());
				if (loginDto.getAnswer() != null && !"".equals(loginDto.getAnswer())) {
					logger.info(loginDto.getAnswer());
					sellerDto.setQuMobileNo(loginDto.getQuestion());
					sellerDto.setCustMobileNo(loginDto.getAnswer());
					sellerResponseList.add(loginDto.getQuestion());
					sellerResponseList.add(loginDto.getAnswer());
					sellerResponseList.add(formatter.format(new Date()));
					session.setAttribute(Constants.SELLERRESPONSELIST, sellerResponseList);
					session.setAttribute(Constants.LOGIN, sellerDto);
				}
				logger.info("Returning from controller :: stepH3");
				return preissuancejsp + "step-3";
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured inside stepH3 :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info(Constants.UNAUTHORIZEDACCESS);
		return preissuancejsp + Constants.LOGIN;
	}

	@RequestMapping(value = "step-4", method = { RequestMethod.POST })
	public String stepH4(@ModelAttribute("loginDto") LoginDto loginDto, Model model) {
		try {
			SellerDto sellerDto = (SellerDto) session.getAttribute(Constants.LOGIN);
			List<String> sellerResponseList = (List<String>) session.getAttribute(Constants.SELLERRESPONSELIST);
			logger.info(sellerDto);
			if (sellerDto != null && !"".equalsIgnoreCase(sellerDto.getTxnId())) {
				if (sellerDto.getCustPersistencyType() != null
						&& !"".equalsIgnoreCase(sellerDto.getCustPersistencyType())) {
					model.addAttribute("persistencyType", sellerDto.getCustPersistencyType());

				}
				if (loginDto.getAnswer() != null && !"".equals(loginDto.getAnswer())) {
					ThreadContext.push("step-4 : " + sellerDto.getTxnId());
					logger.info(loginDto.getAnswer());
					sellerDto.setQuEmailId(loginDto.getQuestion());
					sellerDto.setCustEmailId(loginDto.getAnswer());
					sellerResponseList.add(loginDto.getQuestion());
					sellerResponseList.add(loginDto.getAnswer());
					sellerResponseList.add(formatter.format(new Date()));
					session.setAttribute(Constants.SELLERRESPONSELIST, sellerResponseList);
					session.setAttribute(Constants.LOGIN, sellerDto);
				}
				logger.info("Returning from controller :: stepH4");
				return preissuancejsp + "step-4";
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured inside stepH4 :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info(Constants.UNAUTHORIZEDACCESS);
		return preissuancejsp + Constants.LOGIN;
	}

	@RequestMapping(value = "step-5", method = { RequestMethod.POST })
	public String stepH5(@ModelAttribute("loginDto") LoginDto loginDto, Model model) {
		try {
			SellerDto sellerDto = (SellerDto) session.getAttribute(Constants.LOGIN);
			List<String> sellerResponseList = (List<String>) session.getAttribute(Constants.SELLERRESPONSELIST);
			if (sellerDto != null && !"".equalsIgnoreCase(sellerDto.getTxnId())) {
				ThreadContext.push("step-5 : " + sellerDto.getTxnId());
				String persistencyType;
				sellerDto.setQuPersistencyType(loginDto.getQuestion());
				sellerResponseList.add(loginDto.getQuestion());
				if (loginDto.getAnswer() != null && !"".equals(loginDto.getAnswer())) {
					sellerDto.setCustPersistencyType(loginDto.getAnswer());
					sellerResponseList.add(loginDto.getAnswer());
					persistencyType = loginDto.getAnswer();
				} else {
					persistencyType = sellerDto.getCustPersistencyType();
					sellerResponseList.add(persistencyType);
				}
				sellerResponseList.add(formatter.format(new Date()));
				session.setAttribute(Constants.SELLERRESPONSELIST, sellerResponseList);
				session.setAttribute(Constants.LOGIN, sellerDto);
				logger.info("Getting Beneficaryname : start");
				List<BenefitNames> benefitNames = preissuanceService.getSellerBenifitNames(persistencyType);
				logger.info("Getting Beneficaryname : end");
				model.addAttribute("benefitNames", benefitNames);
				logger.info("Returning from controller :: stepH5");
				return preissuancejsp + "step-5";
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured inside stepH5 :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info(Constants.UNAUTHORIZEDACCESS);
		return preissuancejsp + Constants.LOGIN;
	}

	@RequestMapping(value = "step-6", method = { RequestMethod.POST })
	public String stepH6(@ModelAttribute("loginDto") LoginDto loginDto, Model model, HttpServletRequest request) {
		try {
			SellerDto sellerDto = (SellerDto) session.getAttribute(Constants.LOGIN);
			List<String> sellerResponseList = (List<String>) session.getAttribute(Constants.SELLERRESPONSELIST);
			if (sellerDto != null && !"".equalsIgnoreCase(sellerDto.getTxnId())) {
				Map<String, String> namePremiumTermPeriodMap = preissuanceService
						.getBenifitNameAndPremiumPaymentTermMap(sellerDto.getCustPersistencyType());
				ThreadContext.push("step-6 : " + sellerDto.getTxnId());
				if (loginDto.getAnswer() != null && !"".equals(loginDto.getAnswer())) {
					logger.info(loginDto.getAnswer());
					sellerDto.setQuBenefitName(loginDto.getQuestion());
					String values[] = loginDto.getAnswer().split("::");
					sellerDto.setCustBenefitName(values[0]);
					sellerDto.setCustGroupId(values[1]);
					sellerResponseList.add(loginDto.getQuestion());
					sellerResponseList.add(values[0]);
					sellerResponseList.add(formatter.format(new Date()));
					session.setAttribute(Constants.SELLERRESPONSELIST, sellerResponseList);
					model.addAttribute("map",
							namePremiumTermPeriodMap.get(sellerDto.getCustBenefitName().trim()) != null
									? namePremiumTermPeriodMap.get(sellerDto.getCustBenefitName().trim()) : "");
					session.setAttribute(Constants.LOGIN, sellerDto);
				}
				logger.info("Returning from controller :: stepH6 ");
				return preissuancejsp + "step-6";
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured in stepH6 :: " + ex);
		} finally {
			ThreadContext.pop();
		}

		logger.info(Constants.UNAUTHORIZEDACCESS);
		return preissuancejsp + Constants.LOGIN;
	}

	@RequestMapping(value = "step-7", method = { RequestMethod.POST })
	public String stepH7(@ModelAttribute("loginDto") LoginDto loginDto, Model model) {
		try {
			SellerDto sellerDto = (SellerDto) session.getAttribute(Constants.LOGIN);
			List<String> sellerResponseList = (List<String>) session.getAttribute(Constants.SELLERRESPONSELIST);
			if (sellerDto != null && !"".equalsIgnoreCase(sellerDto.getTxnId())) {
				if (sellerDto.getCustPremiumThro() != null && !"".equalsIgnoreCase(sellerDto.getCustPremiumThro())) {
					model.addAttribute("premiumThrough", sellerDto.getCustPremiumThro());
				}
				ThreadContext.push("step-7 : " + sellerDto.getTxnId());
				if (loginDto.getAnswer() != null && !"".equals(loginDto.getAnswer())) {
					logger.info(loginDto.getAnswer());
					sellerDto.setQuTermPeriod(loginDto.getQuestion());
					sellerDto.setCustTermPeriod(loginDto.getAnswer());
					sellerResponseList.add(loginDto.getQuestion());
					sellerResponseList.add(loginDto.getAnswer());
					sellerResponseList.add(formatter.format(new Date()));
					session.setAttribute(Constants.SELLERRESPONSELIST, sellerResponseList);
					session.setAttribute(Constants.LOGIN, sellerDto);
				}
				logger.info("Returning from controller :: stepH7");
				return preissuancejsp + "step-7";
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured inside stepH7 :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info(Constants.UNAUTHORIZEDACCESS);
		return preissuancejsp + Constants.LOGIN;
	}

	@RequestMapping(value = "step-8", method = { RequestMethod.POST })
	public String stepH8(@ModelAttribute("loginDto") LoginDto loginDto, Model model) {
		try {
			SellerDto sellerDto = (SellerDto) session.getAttribute(Constants.LOGIN);
			List<String> sellerResponseList = (List<String>) session.getAttribute(Constants.SELLERRESPONSELIST);
			if (sellerDto != null && !"".equalsIgnoreCase(sellerDto.getTxnId())) {
				ThreadContext.push("step-8 : " + sellerDto.getTxnId());
				if (loginDto.getAnswer() != null && !"".equals(loginDto.getAnswer())) {
					logger.info(loginDto.getAnswer());
					sellerDto.setQuPremiumThro(loginDto.getQuestion());
					sellerDto.setCustPremiumThro(loginDto.getAnswer());
					sellerResponseList.add(loginDto.getQuestion());
					sellerResponseList.add(loginDto.getAnswer());
					sellerResponseList.add(formatter.format(new Date()));
					session.setAttribute(Constants.SELLERRESPONSELIST, sellerResponseList);
					session.setAttribute(Constants.LOGIN, sellerDto);
				}
				logger.info("Returning from controller :: stepH8");
				return preissuancejsp + "step-8";
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured inside stepH8 :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info(Constants.UNAUTHORIZEDACCESS);
		return preissuancejsp + Constants.LOGIN;
	}

	@RequestMapping(value = "step-9", method = { RequestMethod.POST })
	public String stepH9(@ModelAttribute("loginDto") LoginDto loginDto, Model model) {
		try {
			SellerDto sellerDto = (SellerDto) session.getAttribute(Constants.LOGIN);
			List<String> sellerResponseList = (List<String>) session.getAttribute(Constants.SELLERRESPONSELIST);
			if (sellerDto != null && !"".equalsIgnoreCase(sellerDto.getTxnId())) {
				ThreadContext.push("step-9 : " + sellerDto.getTxnId());
				if (loginDto.getAnswer() != null && !"".equals(loginDto.getAnswer())) {
					logger.info(loginDto.getAnswer());
					sellerDto.setQuSumAsrdAplid(loginDto.getQuestion());
					sellerDto.setCustSumAsrdAplid(loginDto.getAnswer());
					sellerResponseList.add(loginDto.getQuestion());
					sellerResponseList.add(loginDto.getAnswer());
					sellerResponseList.add(formatter.format(new Date()));
					session.setAttribute(Constants.SELLERRESPONSELIST, sellerResponseList);
					session.setAttribute(Constants.LOGIN, sellerDto);
				}
				logger.info("Returning from controller :: stepH9");
				return preissuancejsp + "step-9";
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured in stepH9 :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info(Constants.UNAUTHORIZEDACCESS);
		return preissuancejsp + Constants.LOGIN;
	}

	@RequestMapping(value = "step-10", method = { RequestMethod.POST })
	public String stepH10(@ModelAttribute("loginDto") LoginDto loginDto, Model model) {
		try {
			SellerDto sellerDto = (SellerDto) session.getAttribute(Constants.LOGIN);
			List<String> sellerResponseList = (List<String>) session.getAttribute(Constants.SELLERRESPONSELIST);
			if (sellerDto != null && !"".equalsIgnoreCase(sellerDto.getTxnId())) {
				ThreadContext.push("step-10 : " + sellerDto.getTxnId());
				if (loginDto.getAnswer() != null && !"".equals(loginDto.getAnswer())) {
					logger.info(loginDto.getAnswer());
					sellerDto.setQuIniPremPay(loginDto.getQuestion());
					sellerDto.setCustIniPremPay(loginDto.getAnswer());
					sellerResponseList.add(loginDto.getQuestion());
					sellerResponseList.add(loginDto.getAnswer());
					sellerResponseList.add(formatter.format(new Date()));
					session.setAttribute(Constants.SELLERRESPONSELIST, sellerResponseList);
					session.setAttribute(Constants.LOGIN, sellerDto);
				}
				logger.info("Returning from controller :: stepH10 ");
				return preissuancejsp + "step-10";
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured inside stepH10 :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info(Constants.UNAUTHORIZEDACCESS);
		return preissuancejsp + Constants.LOGIN;
	}

	@RequestMapping(value = "step-final", method = { RequestMethod.POST })
	public String stepFinal(@ModelAttribute("loginDto") LoginDto loginDto, Model model) {
		try {
			SellerDto sellerDto = (SellerDto) session.getAttribute(Constants.LOGIN);
			List<String> sellerResponseList = (List<String>) session.getAttribute(Constants.SELLERRESPONSELIST);
			if (sellerDto != null && !"".equalsIgnoreCase(sellerDto.getTxnId())) {
				ThreadContext.push("step-final : " + sellerDto.getTxnId());
				if (loginDto.getAnswer() != null && !"".equals(loginDto.getAnswer())) {
					logger.info(loginDto.getAnswer());
					sellerDto.setQuMaturityPeriod(loginDto.getQuestion());
					sellerDto.setCustMaturityPeriod(loginDto.getAnswer());
					sellerResponseList.add(loginDto.getQuestion());
					sellerResponseList.add(loginDto.getAnswer());
					sellerResponseList.add(formatter.format(new Date()));
					session.setAttribute(Constants.SELLERRESPONSELIST, sellerResponseList);
					session.setAttribute(Constants.LOGIN, sellerDto);
				}
				// Save Data in DB

				if (!sellerDto.getCustFName().isEmpty() && !sellerDto.getCustLName().isEmpty()
						&& !sellerDto.getCustMobileNo().isEmpty() && !sellerDto.getCustBenefitName().isEmpty()
						&& !sellerDto.getCustPersistencyType().isEmpty() && !sellerDto.getCustGroupId().isEmpty()
						&& !sellerDto.getCustIniPremPay().isEmpty() && !sellerDto.getCustPremiumThro().isEmpty()
						&& !sellerDto.getCustSumAsrdAplid().isEmpty() && !sellerDto.getCustTermPeriod().isEmpty()
						&& !sellerDto.getCustMaturityPeriod().isEmpty()) {
					logger.info("Saving data in DB : Start");
					boolean updateStatus = preissuanceService.saveSellerRecords(sellerDto);
					logger.info("Saving data in DB : End : " + updateStatus);
					if (updateStatus) {
						String agentMoNo = sellerDto.getAgentMobileNumber();
						String customerMoNo = sellerDto.getCustMobileNo();
						logger.info("Agent MoNo : " + agentMoNo + " : Custumer MoNo : " + customerMoNo);
						if (!customerMoNo.equalsIgnoreCase(agentMoNo)) {
							// Send SMS and Mail
							if (sellerDto.getCustEmailId() != null && !sellerDto.getCustEmailId().isEmpty()
									&& !"null".equalsIgnoreCase(sellerDto.getCustEmailId())) {
								logger.info("send Mail : Start");
								boolean mailSend = preissuanceService.sendMail(sellerDto);
								logger.info("send Mail : End : " + mailSend);
							}

							logger.info("send SMS : Start");
							boolean smsSend = preissuanceService.sendSms(sellerDto);
							logger.info("send SMS : End : " + smsSend);
							if (smsSend) {
								logger.info("Updating data in DB For SMS : Start");
								boolean smsUpdateStatus = preissuanceService.updateSellerRecords(sellerDto, "Y");
								logger.info("Updating data in DB For SMS : End : " + smsUpdateStatus);
							}
							Boolean responseCapture = preissuanceService.writeSellerResponseIntoFile(sellerResponseList,
									sellerDto.getTxnId());
							logger.info("Response Capture CSV write status for Txn Id " + sellerDto.getTxnId()
									+ responseCapture);
							session.removeAttribute(Constants.LOGIN);
							session.removeAttribute(Constants.SELLERRESPONSELIST);
							return preissuancejsp + Constants.SUCCESS;
						} else {
							logger.info("Both Customer and Agent Mobile No matched so Result is Failure");
							session.removeAttribute(Constants.LOGIN);
							session.removeAttribute(Constants.SELLERRESPONSELIST);
							return preissuancejsp + "failure";
						}
					} else {
						logger.error("Data not saved in DB thats why Failure message shown");
						session.removeAttribute(Constants.LOGIN);
						session.removeAttribute(Constants.SELLERRESPONSELIST);
						return preissuancejsp + "failure";
					}
				} else {
					logger.info("Un Authorized Access Mendatory parameters are missing at final step");
					session.removeAttribute(Constants.LOGIN);
					session.removeAttribute(Constants.SELLERRESPONSELIST);
					return preissuancejsp + Constants.ERRORPAGE;
				}
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured inside stepFinal :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info(Constants.UNAUTHORIZEDACCESS);
		return preissuancejsp + Constants.LOGIN;
	}

	@RequestMapping(value = "/buyer", method = { RequestMethod.GET, RequestMethod.POST })
	public String welcomeNote(@RequestParam(name = Constants.TXNID, required = false) String txnId1, Model model) {
		try {
			String txnId = txnId1;
			if (txnId != null) {
				try {

					ThreadContext.push("buyer : " + txnId);
					logger.info("Customer Start his/her journey");
					session.removeAttribute(Constants.TXNID);
					session.removeAttribute(Constants.SLIDEQUESTIONDTOS);
					session.removeAttribute(Constants.SELLERDTO);
					if (txnId.contains(" ")) {
						txnId = txnId.replaceAll(" ", "+");
					}
					String txnIdWithSource = AESEncryptor.decrypt(txnId);

					String txnIde = txnIdWithSource.split(Pattern.quote("||"))[0];

					String source = txnIdWithSource.split(Pattern.quote("||"))[1];
					String appSource = txnIdWithSource.split(Pattern.quote("||"))[2];
					logger.info("Updating verification source fro TxndId START" + txnIde);
					Boolean updatedSource = preissuanceService.updateVerificationSource(txnIde, source, appSource);
					logger.info("Updating verification source fro TxndId START" + txnIde + "updated status "
							+ updatedSource);
					Boolean emailCheck = preissuanceService.checkEmailId(txnIde, appSource);
					List<SlideQuestionDto> slideQuestionDtos = preissuanceService.getSlideQuestionsByTxnId(txnIde,
							emailCheck, appSource);
					List<SlideQuestionDto> slideQuestionAnsDtoList = preissuanceService
							.getSlideQuestionsAnsByCust(txnIde, appSource);
					if (slideQuestionAnsDtoList != null && !slideQuestionAnsDtoList.isEmpty()
							&& (slideQuestionDtos.size() == slideQuestionAnsDtoList.size())) {
						logger.info("Already Attempted so going to show link expire");
						return preissuancejsp + "expireLink";
					} else if (slideQuestionAnsDtoList != null && !slideQuestionAnsDtoList.isEmpty()
							&& (slideQuestionAnsDtoList.size() < slideQuestionDtos.size())) {
						session.setAttribute(Constants.TXNID, txnIde);
						session.setAttribute("appSource", appSource);
						session.setAttribute(Constants.SLIDEQUESTIONDTOS, slideQuestionDtos);
						int percentage;
						percentage = slideQuestionDtos.size() + 1;
						percentage = (slideQuestionAnsDtoList.size() * 100) / percentage;
						model.addAttribute("percent", percentage);
						SlideQuestionDto retDto = slideQuestionDtos.get(slideQuestionAnsDtoList.size());
						retDto.setPercent(percentage);
						model.addAttribute("questionDto", retDto);
						logger.info("Survey start from where its end : " + txnIde);
						return preissuancejsp + "customer-sec";
					} else {
						session.setAttribute(Constants.TXNID, txnIde);
						session.setAttribute("appSource", appSource);
						logger.info("Returning to welcome page : " + txnIde);
						return preissuancejsp + "welcome-note";
					}

				} catch (Exception ex) {
					logger.info("Invalid TXN ID : Unable to decript : " + txnId);
					logger.error("Exception :: " + ex);
				}
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured : " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("Un Authorized Access Throwing customer to error page");
		return preissuancejsp + Constants.ERRORPAGE;
	}

	@RequestMapping(value = "/mprobuyer", method = { RequestMethod.GET, RequestMethod.POST })
	public String welcomeMproBuyer(@RequestParam(name = Constants.TXNID, required = false) String txnId, Model model,
			HttpSession session) {

		logger.info("PreissuanceControllerMVC : welcomeMproBuyer() : START");
		int totalQstns = 0;
		String redirectionPage = preissuancejsp;
		boolean isWopInsured = false;
		String selfiEnableChannelName = env.getProperty("com.qc.preissuance.imagecapture.channelname");
		Map<String, List<SlideMproQuestionDto>> slideQuestionDtos = null;
		try {
			String txnIdWithSource = AESEncryptor.decrypt(txnId);
			String txnIde = txnIdWithSource.split(Pattern.quote("||"))[0];
			String source = txnIdWithSource.split(Pattern.quote("||"))[1];
			String appSource = txnIdWithSource.split(Pattern.quote("||"))[2];
			String url = request.getRequestURL().toString();
			String agtChannelCode;
			session.setAttribute("TxnId", txnId);
			// ADDED IN SESSION FOR POSV MULTI ENV
			String linkViewed = request.getParameter("linkViewed");
			String directLink = request.getParameter("directLink");
			String selefieCaptured = request.getParameter("isSelfieCaptured");// selfie
																				// @RK01318
			session.setAttribute("URL", url);
			session.setAttribute("appSource", appSource);
			// ADDED IN SESSION FOR POSV MULTI ENV

			// @RK01318 Starts Axis Channel
			String transaction[] = preissuanceService.checkForAxisTransaction(txnIde);
			boolean axispolicyOrNot = preissuanceService.checkAxisPolicyOrNot(transaction);
			// @RK01318 Starts Axis Channel

			int isExistingUser;
			if (axispolicyOrNot) {
				isExistingUser = preissuanceService.checkUserJourney(transaction[1], appSource);
			} else {
				isExistingUser = preissuanceService.checkUserJourney(txnIde, appSource);
			}

			String[] checkTxn;
			if (axispolicyOrNot) {
				checkTxn = preissuanceService.checkTransaction(transaction[1]);
				agtChannelCode = preissuanceService.checkAgtChannelCode(transaction[1]);
				logger.info("Agent Channel Code is :: " + agtChannelCode);
				session.setAttribute("AgtChannelCode", agtChannelCode);
				// @RK01318 Starts
				logger.info("setting isWopInsured in session start");
				isWopInsured = preissuanceService.isInsurerApplicable(transaction[1]);
				session.setAttribute("isWopInsured", isWopInsured);
				logger.info("setting isWopInsured in session ends");
				// @RK01318 Ends
			} else {
				checkTxn = preissuanceService.checkTransaction(txnIde);
				agtChannelCode = preissuanceService.checkAgtChannelCode(txnIde);
				logger.info("Agent Channel Code is :: " + agtChannelCode);
				session.setAttribute("AgtChannelCode", agtChannelCode);
				// @RK01318 Starts
				logger.info("setting isWopInsured in session start");
				isWopInsured = preissuanceService.isInsurerApplicable(txnIde);
				session.setAttribute("isWopInsured", isWopInsured);
				logger.info("setting isWopInsured in session ends");

			}

			newJspCheck = session.getAttribute("newJspCheck") + "";
			if (checkTxn != null && !"true".equals(linkViewed) && isExistingUser <= 0) {
				logger.info(
						"Illustration Link has values :: " + checkTxn[0] + " ::PFLink  has value :: " + checkTxn[1]);
				session.setAttribute("IllustrationLink", checkTxn[0]);
				session.setAttribute("pfLink", checkTxn[1]);
				return redirectionPage += "/welcome-noteNew";
			} else if (!"true".equals(selefieCaptured) && selfiEnableChannelName.equalsIgnoreCase(agtChannelCode)) {
				return redirectionPage += "/selfie";
			} else {

				String selfiImage = request.getParameter("selfiImage");
				if (!StringUtility.checkFieldIsNull(selfiImage)) {
					preissuanceService.saveSelfi(selfiImage, txnIde, axispolicyOrNot);
				}

				ThreadContext.push("mprobuyer : " + txnId);
				session.setAttribute("mpro_txn_id", txnIde);
				logger.info("Source " + source + " for txn id " + txnIde);
				// @RK01318 CHECKING IF THE TXNID IS FOR AXIS 2 POLICIES STARTS

				if (axispolicyOrNot) {
					session.setAttribute("axistransaction", transaction);
					String customerBenifitName = preissuanceService.getCustomerBenefitName(transaction[1]);
					session.setAttribute("customerBenifitName", customerBenifitName);
				}
				session.setAttribute("axispolicyOrNot", axispolicyOrNot);
				// Added BY @RK01318 STARTS

				// Added BY @RK01318 STARTS
				Boolean updatedSource;
				if (axispolicyOrNot) {
					updatedSource = preissuanceService.updateMproVerificationSource(transaction[0], source);
					logger.info("Updating mpro verification source for Txnd Id " + transaction[0] + "updated status "
							+ updatedSource);
					updatedSource = preissuanceService.updateMproVerificationSource(transaction[1], source);
					logger.info("Updating mpro verification source for Txnd Id " + transaction[1] + "updated status "
							+ updatedSource);
				} else {
					updatedSource = preissuanceService.updateMproVerificationSource(txnIde, source);
					logger.info("Updating mpro verification source for Txnd Id " + txnIde + "updated status "
							+ updatedSource);
				}
				// @RK01318 CHECKING IF THE TXNID IS FOR AXIS 2 POLICIES ENDS

				// @RK01318 starts
				boolean checkExpiryLink;
				if (axispolicyOrNot) {
					checkExpiryLink = preissuanceService.checkExpiryLink(txnIde, transaction);
				} else {
					checkExpiryLink = preissuanceService.checkExpiryLink(txnIde, null);
				}
				// @RK01318 Starts
				boolean validateOtpEntered;
				if (axispolicyOrNot) {
					validateOtpEntered = preissuanceService.validateOtpEnteredTime(txnIde, transaction);
				} else {
					validateOtpEntered = preissuanceService.validateOtpEnteredTime(txnIde, null);
				}
				// @RK01318 Starts

				if (checkExpiryLink) {
					logger.info("Link expired for txnid " + txnIde);
					redirectionPage += "/mproErrorPage";
				} else if (!validateOtpEntered) {
					logger.info("Otp '1 hour' check failed for the user for txnid" + txnIde);
					redirectionPage += "/mproErrorPage";
				} else {
					Map<String, PiSellerTxnDto> sellerTxnDto;
					if (axispolicyOrNot) {
						sellerTxnDto = preissuanceService.getPiSellerTxnData(transaction[1], txnIde, appSource);
						for (Entry<String, PiSellerTxnDto> entry : sellerTxnDto.entrySet()) {
							PiSellerTxnDto values = entry.getValue();
							session.setAttribute("lastDigit", values.getLastMobileNumber());
						}
					} else {
						sellerTxnDto = preissuanceService.getPiSellerTxnData(txnIde, null, appSource);
						for (Entry<String, PiSellerTxnDto> entry : sellerTxnDto.entrySet()) {
							PiSellerTxnDto values = entry.getValue();
							session.setAttribute("lastDigit", values.getLastMobileNumber());
						}
					}

					logger.info("seller record found and is saved to session.");
					session.setAttribute("seller_mpro_dto", sellerTxnDto);
					List<String> listType;
					if (axispolicyOrNot) {
						listType = preissuanceService.getMproQuestionDetails(transaction[1]);
					} else {
						listType = preissuanceService.getMproQuestionDetails(txnIde);
					}

					if (listType != null && !listType.isEmpty()) {
						logger.info("Mpro sequence for txnid: " + txnIde + " found. value :" + listType.toString());
						logger.info("Mpro getValidPageSequence: START");
						String[] sequence = preissuanceService.getValidPageSequence(listType);
						logger.info("Mpro getValidPageSequence: END");
						session.setAttribute("validjsplist", sequence);
						session.setAttribute("listType", listType);
						redirectionPage += preissuanceService.getNextJspPage(sequence, "home", appSource);

						// fetch questions for product.
						if (txnIde != null && !txnIde.isEmpty()) {
							logger.info("Mpro getMproSlideQuestionsByTxnId for txnid:" + txnIde + " : START");

							// @RK01318 Starts
							if (axispolicyOrNot) {
								logger.info("Axis Channel policy transactionId::" + transaction[1]);
								slideQuestionDtos = preissuanceService.getMproSlideQuestionsByTxnId(transaction[1],
										false, appSource, txnIde);
								MProBuyerDetailsDTO mProBuyerDetailsDTOIsAvailable = preissuanceService
										.getPreviousBuyerChoice(
												slideQuestionDtos.get(transaction[1]).get(0).getQuestionid(),
												transaction[1]);
								model.addAttribute("currentMProBuyerDetailsDTO", mProBuyerDetailsDTOIsAvailable);
							} else {
								slideQuestionDtos = preissuanceService.getMproSlideQuestionsByTxnId(txnIde, false,
										appSource, null);
								MProBuyerDetailsDTO mProBuyerDetailsDTOIsAvailable = preissuanceService
										.getPreviousBuyerChoice(slideQuestionDtos.get(txnIde).get(0).getQuestionid(),
												txnIde);
								model.addAttribute("currentMProBuyerDetailsDTO", mProBuyerDetailsDTOIsAvailable);
							}
							// Find total number of question :: RR1517 :: starts
							int productQstnsOneTxn;
							int totalproductQuestions = 0;
							String questnId;
							int unRequiredQuestions = 0;

							if (axispolicyOrNot) {
								if (listType != null && !listType.isEmpty() && listType.contains("PRODUCT")) {
									for (int s = 0; s < transaction.length; s++) {
										List<SlideMproQuestionDto> slideQuestionList = slideQuestionDtos
												.get(transaction[s]);
										productQstnsOneTxn = slideQuestionList.size();
										totalproductQuestions = totalproductQuestions + productQstnsOneTxn;
										for (int i = 0; i < productQstnsOneTxn; i++) {
											questnId = slideQuestionDtos.get(transaction[s]).get(i).getQuestionid();
											if ("P28".equalsIgnoreCase(questnId) && !"X"
													.equalsIgnoreCase(session.getAttribute("AgtChannelCode") + "")) {
												unRequiredQuestions++;
											}
											if ("P5".equals(slideQuestionList.get(i).getQuestionid())
													&& !"ECS".equalsIgnoreCase(
															sellerTxnDto.get(transaction[s]).getEcsDetails())) {
												unRequiredQuestions++;
											}

										}
									}
								}
							} else {
								if (listType != null && !listType.isEmpty() && listType.contains("PRODUCT")) {
									for (List<SlideMproQuestionDto> slideQuestionList : slideQuestionDtos.values()) {
										productQstnsOneTxn = slideQuestionList.size();
										totalproductQuestions = totalproductQuestions + productQstnsOneTxn;
										for (int i = 0; i < productQstnsOneTxn; i++) {
											questnId = slideQuestionDtos.get(txnIde).get(i).getQuestionid();
											if ("P28".equalsIgnoreCase(questnId) && !"X"
													.equalsIgnoreCase(session.getAttribute("AgtChannelCode") + "")) {
												unRequiredQuestions++;
											}
											if ("P5".equals(slideQuestionList.get(i).getQuestionid()) && !"ECS"
													.equalsIgnoreCase(sellerTxnDto.get(txnIde).getEcsDetails())) {
												unRequiredQuestions++;
											}

										}
									}
								}
							}

							int healthQstns;
							int cancerQstns;
							int psmQstns;
							int replacementQstns;
							int cancerHealthQstns;
							for (String type : listType) {
								if ("PRODUCT".equalsIgnoreCase(type)) {
									logger.info("Count for ||PRODUCT|| ::" + totalproductQuestions);
									totalQstns = totalQstns + totalproductQuestions;
								} else if ("HEALTH".equalsIgnoreCase(type)) {
									healthQstns = 14;

									logger.info("Count for ||HEALTH|| ::" + healthQstns);
									totalQstns = totalQstns + healthQstns;
									// Wop Rider changes 22-May-2019 starts
									if (isWopInsured) {
										totalQstns = totalQstns + healthQstns;
									}
									// Wop Rider changes 22-May-2019 ends
									String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
									if ("true".equals(axispolicyOrNot)) {
										mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH,
												mproTxnId);
									}
									Map<String, String> healthAnswerSurveyMap = preissuanceService
											.getCancerAnswerDetails("Health", mproTxnId, "mpro");
									model.addAttribute("healthAnswerSurveyMap", healthAnswerSurveyMap);

								} else if ("CANCER".equalsIgnoreCase(type)) {
									cancerQstns = 6;
									logger.info("Count for ||CANCER|| ::" + cancerQstns);
									totalQstns = totalQstns + cancerQstns;
									// Wop Rider changes 22-May-2019 starts
									if (isWopInsured) {
										totalQstns = totalQstns + cancerQstns;
									}
									// Wop Rider changes 22-May-2019 ends
									String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
									if ("true".equals(axispolicyOrNot)) {
										mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.CANCER,
												mproTxnId);
									}
									Map<String, String> mproCancerMap = preissuanceService
											.getCancerAnswerDetails("Cancer", mproTxnId, "mpro");
									model.addAttribute("cancerSurveyAnswerMap", mproCancerMap);
								} else if ("PSM".equalsIgnoreCase(type)) {
									psmQstns = 3;
									logger.info("Count for ||PSM|| ::" + psmQstns);
									totalQstns = totalQstns + psmQstns;
								} else if ("REPLACEMENTSALE".equalsIgnoreCase(type)) {
									replacementQstns = 1;
									logger.info("Count for ||REPLACEMENTSALE|| ::" + replacementQstns);
									totalQstns = totalQstns + replacementQstns;
								} else if ("CANCER,HEALTH".equals(type)) {
									cancerHealthQstns = 21;
									logger.info("Count for ||CANCER,HEALTH|| ::" + cancerHealthQstns);
									totalQstns = totalQstns + cancerHealthQstns;
									if (isWopInsured) {
										totalQstns = totalQstns + cancerHealthQstns;
									}
									String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
									if ("true".equals(axispolicyOrNot)) {
										mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.CANCER,
												mproTxnId);
									}
									Map<String, String> mproCancerMap = preissuanceService
											.getCancerAnswerDetails("Cancer", mproTxnId, "mpro");
									model.addAttribute("cancerSurveyAnswerMap", mproCancerMap);
								}
							}

							logger.info("Total number of Product Questions" + totalQstns + "  UnRequired Questions are "
									+ unRequiredQuestions);
							session.setAttribute("TotalQstns", totalQstns - unRequiredQuestions);
							session.setAttribute("AttemptedQstns", 0);

							// @RK01318 Ends
							logger.info("Mpro getMproSlideQuestionsByTxnId for txnid:" + txnIde + " : END");

							logger.info("Questions for product fetched successfully and set to session.");
							session.setAttribute("question_pos", 0);
							// @RK01318 STARTS
							if (axispolicyOrNot) {
								session.setAttribute("transactionCount", 1);
								session.setAttribute("productQuestionDto",
										slideQuestionDtos.get(transaction[1]).get(0));
							} else {
								session.setAttribute("transactionCount", 0);
								session.setAttribute("productQuestionDto", slideQuestionDtos.get(txnIde).get(0));
							}
							// @RK01318 ENDS
							session.setAttribute(Constants.SLIDEQUESTIONDTOS, slideQuestionDtos);

						}

						if (isExistingUser > 0 && !"false".equals(directLink)
								&& !selfiEnableChannelName.equalsIgnoreCase(agtChannelCode)) {
							// existing user
							logger.info("Redirecting, where customer leaved in last attempt with txn-id : " + txnIde);
							logger.info("Mpro getMproSlideQuestionsByTxnId for txnid:" + txnIde + " : START");
							ExistingUserDTO redirect;
							if (axispolicyOrNot && slideQuestionDtos != null) {

								boolean isProductComplete = preissuanceService.isTransactionCompleteProductQuestion(
										transaction[1], slideQuestionDtos.get(transaction[1]).get(0).getGroupId(),
										"ECS".equalsIgnoreCase(sellerTxnDto.get(transaction[1]).getEcsDetails())); // isTransactionCompleteProductQuestion
								if (isProductComplete) {
									redirect = preissuanceService.redirectToPage(transaction[0], appSource,
											isWopInsured);
									session.setAttribute("transactionCount", 0);
								} else {
									redirect = preissuanceService.redirectToPage(transaction[1], appSource,
											isWopInsured);
								}
							} else {
								redirect = preissuanceService.redirectToPage(txnIde, appSource, isWopInsured);
							}

							request.getSession().setAttribute("question_pos", redirect.getSequenceNumber());
							redirectionPage = "forward:" + redirect.getUrl() + "?isPrev=true&directUrl=true";
						}
					} else {
						redirectionPage += "/mproErrorPage";
					}
				}
			}
		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured : " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("PreissuanceControllerMVC : welcomeMproBuyer() : END");

		return redirectionPage;
	}

	@SuppressWarnings("unchecked")
	@RequestMapping(value = "/product-1", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerProductAns(Model model, HttpServletRequest request) {
		if (errorInGetMethod(request)) {
			return preissuancejsp + "mproErrorPage";
		}
		logger.info("PreissuanceControllerMVC : saveMproBuyerProductAns() : START");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		String redirectionPath = preissuancejsp + "product-1";
		try {
			// Added By RK01318 for restrict null in question and answer
			boolean isQuestionAndAnswerNull = false;
			String appSource = session.getAttribute("appSource") + "";
			String directUrl = request.getParameter("directUrl");
			Integer questpos = (Integer) request.getSession().getAttribute("question_pos");
			String isPrev = request.getParameter("isPrev");
			// Added By RK01318 for restrict null in question and answer
			String txnId = (String) request.getSession().getAttribute("mpro_txn_id");
			// @RK01318 ADDED FOR AXIS POLICY
			String axispolicyOrNot = session.getAttribute("axispolicyOrNot") + "";
			String axisTransactionCount = session.getAttribute("transactionCount") + "";
			String[] transactions = (String[]) session.getAttribute("axistransaction");
			if ("true".equals(axispolicyOrNot)) {
				if (!"null".equals(axisTransactionCount) && !"".equals(axisTransactionCount)) {
					txnId = transactions[Integer.parseInt(axisTransactionCount)];
				}

				String customerBenifitName = preissuanceService.getCustomerBenefitName(txnId);
				session.setAttribute("customerBenifitName", customerBenifitName);
			}

			// @RK01318 ADDED FOR AXIS POLICY
			String platform = request.getParameter("platform");
			String userAgent = request.getParameter("userAgent");

			Map<String, List<SlideMproQuestionDto>> slideQuestionDtosMap = (Map<String, List<SlideMproQuestionDto>>) request
					.getSession().getAttribute(Constants.SLIDEQUESTIONDTOS);

			// @RK01318 STARTS
			if ("true".equals(isPrev)) {
				if ("true".equals(axispolicyOrNot) && "0".equals(axisTransactionCount) && questpos == 1) {
					session.setAttribute("transactionCount", 1);
					questpos = slideQuestionDtosMap.get(transactions[1]).size();
				}
			}
			// @RK01318 ENDS

			List<SlideMproQuestionDto> slideQuestionDtos = slideQuestionDtosMap.get(txnId);

			if (slideQuestionDtos != null && !slideQuestionDtos.isEmpty() && txnId != null) {
				logger.info("SlideQuestion not empty from session for txnid :" + txnId);
				ThreadContext.push("product-section :" + txnId);

				session.removeAttribute("question_pos");
				logger.debug("Product question position fetched as :" + questpos);

				logger.info("Question position for product fetched as :" + questpos + " for txnid:" + txnId);
				if (questpos != null) {
					logger.info("Question position not empty");
					Date currentDate = new java.util.Date();
					String answer = request.getParameter("A_PROD");

					String forward = request.getParameter("forward");
					String category = "Product";
					String createdBy = "mpro";
					String isPrevtemp = request.getParameter("isPrevtemp");
					String isPrevtempCancer = request.getParameter("isPrevtempCancer");
					String isPrevHealth2 = request.getParameter("isPrevHealth2");
					// @ Axis Policy 2 Part
					if ("true".equals(isPrevtemp)) {
						session.setAttribute("AttemptedQstns", attemptedQstns);
					}
					// @ Axis Policy 2 Part Ends
					if ("true".equals(isPrevtempCancer)) {
						attemptedQstns = attemptedQstns - 4;
						session.setAttribute("AttemptedQstns", attemptedQstns);
					}

					if ("true".equals(isPrevHealth2)) {
						attemptedQstns = attemptedQstns - 3;
						session.setAttribute("AttemptedQstns", attemptedQstns);
					}

					if ("true".equals(isPrev) && !"true".equals(directUrl)) {
						questpos--;
						attemptedQstns = attemptedQstns - 1;
						session.setAttribute("AttemptedQstns", attemptedQstns);
					}

					if (slideQuestionDtos.size() > questpos) {
						List<MProBuyerDetailsDTO> questionList = new ArrayList<MProBuyerDetailsDTO>();
						MProBuyerDetailsDTO mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
						logger.info("setting MProBuyerDetailsDTO for txnId:" + txnId + " START");
						mProBuyerDetailsDTO.setTXNID(txnId);
						mProBuyerDetailsDTO.setQSTN_NAME(slideQuestionDtos.get(questpos).getQuestionName());
						mProBuyerDetailsDTO.setANSWER(answer);
						if (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
								|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER())) {
							isQuestionAndAnswerNull = true;
							// ADDED BY RK01318 7-March-2019 STARTS
							addErrorDataInModel(model, mProBuyerDetailsDTO, request);
							// ADDED BY RK01318 7-March-2019 ENDS
						}

						mProBuyerDetailsDTO.setCREATED_DT(currentDate);
						mProBuyerDetailsDTO.setQSTN_CAT(category);
						mProBuyerDetailsDTO.setQSTN_ID(slideQuestionDtos.get(questpos).getQuestionid());
						mProBuyerDetailsDTO.setCREATED_BY(createdBy);
						mProBuyerDetailsDTO.setQuestionType("Primary");
						mProBuyerDetailsDTO.setAnswerType("boolean");
						mProBuyerDetailsDTO.setParentId("NA");
						// Added BY RK01318 starts
						mProBuyerDetailsDTO.setUserAgent(userAgent);
						mProBuyerDetailsDTO.setPlatform(platform);
						// Added By RK01318 Ends
						questionList.add(mProBuyerDetailsDTO);
						logger.info("setting MProBuyerDetailsDTO for txnId:" + txnId + " END");
						if (answer != null)
							logger.info("saveMproBuyerProductAns() saveMproBuyerDetails : START");

						if ((!isQuestionAndAnswerNull || "true".equals(isPrev) || "true".equals(isPrevtemp))
								&& !"true".equals(forward)) {
							// ADDED BY RK01318 7-March-2019 STARTS
							APIErrorDTO errordto = new APIErrorDTO();
							model.addAttribute("error", errordto);
							// ADDED BY RK01318 7-March-2019 ENDS
						}

						if (!"true".equals(isPrevtemp) && !isQuestionAndAnswerNull) {
							preissuanceService.saveMproBuyerDetails(questionList, null, Constants.PRODUCT);
						}
						logger.info("saveMproBuyerProductAns() saveMproBuyerDetails : END");
						if (!isQuestionAndAnswerNull) {
							questpos++;
							logger.info("Question position incremented for product as :" + questpos + " for txnid:"
									+ txnId);

							attemptedQstns = attemptedQstns + 1;
							session.setAttribute("AttemptedQstns", attemptedQstns);

						}
						logger.debug("Product record saved successfully.");
					}

					if (slideQuestionDtos.size() >= (questpos + 1)) {
						// Added by shubham for display ECS question Starts
						PiSellerTxnDto sellerTxnDto = preissuanceService.getPiSellerTxnData(txnId, null, appSource)
								.get(txnId);
						if ("P5".equals(slideQuestionDtos.get(questpos).getQuestionid())
								&& !"ECS".equalsIgnoreCase(sellerTxnDto.getEcsDetails())) {
							if ("true".equals(isPrev) && !"true".equals(directUrl)) {
								questpos--;
							} else if (!"true".equals(directUrl)) {
								questpos++;
								logger.info("Not showing ECS question");
							}
						}
						// Added by shubham for display ECS question Starts

						// Added by Rishabh Rai for showing email question only
						// for Axis channel
						if (slideQuestionDtos.size() >= (questpos + 1)) {
							if ("P28".equals(slideQuestionDtos.get(questpos).getQuestionid())
									&& !"X".equalsIgnoreCase(session.getAttribute("AgtChannelCode") + "")) {
								if ("true".equals(isPrev) && !"true".equals(directUrl)) {
									questpos--;
								} else if (!"true".equals(directUrl))
									questpos++;
								logger.info("Not showing email question other than axis channel");
							}
						}
						// Added by Rishbah Rai for showing email question only
						// for Axis channel

						// Added By Rohit for getting choice of previous
						// question starts
						// questpos>=slideQuestionDtos.size()
						if (questpos >= slideQuestionDtos.size()) {
							if ("1".equals(axisTransactionCount)) {
								int currenttransactioncount = Integer.parseInt(axisTransactionCount);
								session.setAttribute("transactionCount", --currenttransactioncount);
								session.setAttribute("question_pos", 0);
								return "forward:/product-1?forward=true";
							}
							session.setAttribute("question_pos", questpos);
							logger.info("Last question of product is answered.");
							String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");
							if (jspSequence != null) {
								redirectionPath = preissuancejsp
										+ preissuanceService.getNextJspPage(jspSequence, "/product-1", appSource);
								if (redirectionPath.contains("cancer")) {
									String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
									category = "Cancer";
									createdBy = "mpro";
									// Added BY RK01318 STARTS
									if ("true".equals(axispolicyOrNot)) {
										mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.CANCER,
												mproTxnId);
										session.setAttribute("cancerPlan", "true");
									}
									// Added BY RK01318 ENDS
									Map<String, String> mproCancerMap = preissuanceService
											.getCancerAnswerDetails(category, mproTxnId, createdBy);
									model.addAttribute("cancerSurveyAnswerMap", mproCancerMap);
								} else if (redirectionPath.contains("health")) {

									// Added By RK01318 so that previous of
									// health-1 will work starts
									String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
									if ("true".equals(axispolicyOrNot)) {
										mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH,
												mproTxnId);
									}
									Map<String, String> healthAnswerSurveyMap = preissuanceService
											.getCancerAnswerDetails("Health", mproTxnId, "mpro");
									model.addAttribute("healthAnswerSurveyMap", healthAnswerSurveyMap);
									// Added By RK01318 so that previous of
									// health-1 will work ends
								}
							} else {
								redirectionPath = preissuancejsp + "mproErrorPage";
							}
							logger.info("redirecting page to " + redirectionPath
									+ " after answering last question of product.");

						} else {
							MProBuyerDetailsDTO mProBuyerDetailsDTOIsAvailable = preissuanceService
									.getPreviousBuyerChoice(slideQuestionDtos.get(questpos).getQuestionid(), txnId);
							model.addAttribute("currentMProBuyerDetailsDTO", mProBuyerDetailsDTOIsAvailable);
							// Added By Rohit for getting choice of previous
							// question ends
							logger.info("New Question from product list is being sent to product-1");
							session.setAttribute("productQuestionDto", slideQuestionDtos.get(questpos));

							session.setAttribute(Constants.SLIDEQUESTIONDTOS, slideQuestionDtosMap);
							session.setAttribute("question_pos", questpos);
							logger.info("New question pos:" + questpos);
						}

					} else if ("1".equals(axisTransactionCount)) {
						int currenttransactioncount = Integer.parseInt(axisTransactionCount);
						session.setAttribute("transactionCount", --currenttransactioncount);
						session.setAttribute("question_pos", 0);
						return "forward:/product-1?forward=true";
					} else {
						session.setAttribute("question_pos", questpos);
						logger.info("Last question of product is answered.");
						String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");
						if (jspSequence != null) {
							redirectionPath = preissuancejsp
									+ preissuanceService.getNextJspPage(jspSequence, "/product-1", appSource);
							if (redirectionPath.contains("cancer")) {
								String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
								category = "Cancer";
								createdBy = "mpro";
								// Added BY RK01318 STARTS
								if ("true".equals(axispolicyOrNot)) {
									mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.CANCER,
											mproTxnId);
									session.setAttribute("cancerPlan", "true");
								}
								// Added BY RK01318 ENDS
								Map<String, String> mproCancerMap = preissuanceService.getCancerAnswerDetails(category,
										mproTxnId, createdBy);
								model.addAttribute("cancerSurveyAnswerMap", mproCancerMap);
							} else if (redirectionPath.contains("health")) {

								// Added By RK01318 so that previous of health-1
								// will work starts
								String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
								if ("true".equals(axispolicyOrNot)) {
									mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH,
											mproTxnId);
								}
								Map<String, String> healthAnswerSurveyMap = preissuanceService
										.getCancerAnswerDetails("Health", mproTxnId, "mpro");
								model.addAttribute("healthAnswerSurveyMap", healthAnswerSurveyMap);
								// Added By RK01318 so that previous of health-1
								// will work ends
							}
						} else {
							redirectionPath = preissuancejsp + "mproErrorPage";
						}
						logger.info("redirecting page to " + redirectionPath
								+ " after answering last question of product.");
					}
				}
			}

		} catch (Exception e) {
			logger.error("Failed in product screen" + e);
			redirectionPath = preissuancejsp + "mproErrorPage";
		} finally {
			ThreadContext.pop();
			logger.info("PreissuanceControllerMVC : saveMproBuyerProductAns() : END");
		}
		return redirectionPath;
	}

	@RequestMapping(value = "/health-1", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerHealthAns1(Model model, HttpServletRequest request) {

		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}

		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns1() : START");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		boolean isQuestionAndAnswerNull = false;// ADDED BY RK01318
		String isPrev = request.getParameter("isPrev");// ADDED BY RK01318
		try {
			String mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
			ThreadContext.push("health-1 section :" + mpro_txn_id);
			String directUrl = request.getParameter("directUrl");
			String category = "Health";
			Date currentDate = new java.util.Date();
			String createdBy = "mpro";

			// @RK01318 Starts
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";

			if ("true".equals(axispolicyOrNot)) {
				mpro_txn_id = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH, mpro_txn_id);
			}

			// @RK01318 Ends

			// Added By RK01318 so that previous of health-1 will work starts
			String platform = request.getParameter("platform");
			String userAgent = request.getParameter("userAgent");
			String forward = request.getParameter("forward");
			Map<String, String> healthAnswerSurveyMap = preissuanceService.getCancerAnswerDetails("Health", mpro_txn_id,
					"mpro");
			model.addAttribute("healthAnswerSurveyMap", healthAnswerSurveyMap);
			// Added By RK01318 so that previous of health-1 will work ends

			MProBuyerDetailsDTO mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			List<MProBuyerDetailsDTO> questionList = new ArrayList<MProBuyerDetailsDTO>();

			String H1 = request.getParameter("H1");
			String p_a_1 = request.getParameter("p_1");

			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H1);
			mProBuyerDetailsDTO.setANSWER(p_a_1);
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H1");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);

			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(p_a_1)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H1A = request.getParameter("H1A");
				String ra1 = (request.getParameter("r_a_1") == null || "".equals(request.getParameter("r_a_1"))) ? "NA"
						: request.getParameter("r_a_1"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H1A);
				mProBuyerDetailsDTO.setANSWER(ra1);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H1A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H1");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H1B = request.getParameter("H1B");
				String ra2 = (request.getParameter("r_a_2") == null || "".equals(request.getParameter("r_a_2"))) ? "NA"
						: request.getParameter("r_a_2"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H1B);
				mProBuyerDetailsDTO.setANSWER(ra2);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H1B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H1");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H1C = request.getParameter("H1C");
				String r_a_3 = request.getParameter("a_3");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H1C);
				mProBuyerDetailsDTO.setANSWER(r_a_3);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H1C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("boolean");
				mProBuyerDetailsDTO.setParentId("H1");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H1D = request.getParameter("H1D");
				String ra4 = (request.getParameter("r_a_4") == null || "".equals(request.getParameter("r_a_4"))) ? "NA"
						: request.getParameter("r_a_4"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H1D);
				mProBuyerDetailsDTO.setANSWER(ra4);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H1D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H1");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}
			// Question H2 starts

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H2 = request.getParameter("H2");
			String a_h2 = request.getParameter("A_h2");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H2);
			mProBuyerDetailsDTO.setANSWER(a_h2);
			// RK01318 STARTS

			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H2");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(a_h2)) {
				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H2A = request.getParameter("H2A");
				String a_h2a = request.getParameter("a_h2a");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H2A);
				if (a_h2a != null && "on".equals(a_h2a))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H2A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H2");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H2B = request.getParameter("H2B");
				String a_h2b = request.getParameter("a_h2b");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H2B);
				if (a_h2b != null && "on".equals(a_h2b))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");

				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H2B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H2");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H2C = request.getParameter("H2C");
				String a_h2c = request.getParameter("a_h2c");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H2C);
				if (a_h2c != null && "on".equals(a_h2c))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H2C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H2");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H2D = request.getParameter("H2D");
				String ah2d = (request.getParameter("a_h2d") == null || "".equals(request.getParameter("a_h2d"))) ? "NA"
						: request.getParameter("a_h2d"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H2D);
				mProBuyerDetailsDTO.setANSWER(ah2d);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H2D");
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H2");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}

			// Question 3 starts @Added by RR1517
			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H3 = request.getParameter("H3");
			String A_H3 = request.getParameter("A_H3");

			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H3);
			mProBuyerDetailsDTO.setANSWER(A_H3);
			// ADDED BY RK01318 STARTS
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H3");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H3)) {
				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H3A = request.getParameter("H3A");
				String A_H3A = request.getParameter("A_H3A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H3A);
				mProBuyerDetailsDTO.setANSWER(A_H3A);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H3A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("boolean");
				mProBuyerDetailsDTO.setParentId("H3");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H3B = request.getParameter("H3B");
				String aH3B = (request.getParameter("A_H3B") == null || "".equals(request.getParameter("A_H3B"))) ? "NA"
						: request.getParameter("A_H3B"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H3B);
				mProBuyerDetailsDTO.setANSWER(aH3B);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H3B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H3");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

			}

			// Question 3 ends @Added by RR1517
			if ((!isQuestionAndAnswerNull || "true".equals(isPrev)) && !"true".equals(forward)) {
				// ADDED BY RK01318 7-March-2019 STARTS
				APIErrorDTO errordto = new APIErrorDTO();
				model.addAttribute("error", errordto);
				// ADDED BY RK01318 7-March-2019 ENDS
			}

			if (!"true".equals(isPrev) && !isQuestionAndAnswerNull) {
				if ("true".equals(axispolicyOrNot)) {

					mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
					preissuanceService.saveMproBuyerDetails(questionList, mpro_txn_id, Constants.HEALTH);
				} else {
					preissuanceService.saveMproBuyerDetails(questionList, null, Constants.HEALTH);
				}

				attemptedQstns = attemptedQstns + 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			} else if (!"true".equals(directUrl)) {

				attemptedQstns = attemptedQstns - 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			}
		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured saveMproBuyerHealthAns1 : " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("mPro Un Authorized Access Throwing customer to error page");
		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns1() : END");
		List listType = (List) session.getAttribute("listType");
		String mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
		if (isQuestionAndAnswerNull) {

			String axispolicyOrNot = session.getAttribute("axispolicyOrNot") + "";
			if ("true".equals(axispolicyOrNot)) {
				String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
				if (listType.contains("CANCER,HEALTH")) {
					String category = "Cancer";
					String createdBy = "mpro";
					mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.CANCER, mproTxnId);
					Map<String, String> mproCancerMap = preissuanceService.getCancerAnswerDetails(category, mproTxnId,
							createdBy);
					model.addAttribute("cancerSurveyAnswerMap", mproCancerMap);
					return "forward:/cancer-1?isPrevtemp=true&forward=true";
				} else if (listType.contains("PRODUCT")) {
					return "forward:/product-1?isPrevtemp=true&forward=true";
				} else {
					return "forward:/mprobuyer?txnId=" + mpro_txn_id;
				}
			} else {
				if (listType.contains("PRODUCT")) {
					return "forward:/product-1?isPrevtemp=true&forward=true";
				} else {
					return "forward:/mprobuyer?txnId=" + mpro_txn_id;
				}
			}
		} else {
			return preissuancejsp + "health-2";
		}
	}

	@RequestMapping(value = "/health-2", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerHealthAns2(Model model, HttpServletRequest request) {

		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}

		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns2() : START");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		boolean isQuestionAndAnswerNull = false;
		String directUrl = request.getParameter("directUrl");
		String isPrev = request.getParameter("isPrev");
		try {

			String mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
			// @RK01318 Starts
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";

			if ("true".equals(axispolicyOrNot)) {
				mpro_txn_id = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH, mpro_txn_id);
			}

			// @RK01318 Ends
			ThreadContext.push("healt-2 section :" + mpro_txn_id);
			String category = "Health";
			Date currentDate = new java.util.Date();
			String createdBy = "mpro";
			String forward = request.getParameter("forward");

			String platform = request.getParameter("platform");
			String userAgent = request.getParameter("userAgent");

			// Added By RK01318 so that previous of health-1 will work starts
			Map<String, String> healthAnswerSurveyMap = preissuanceService.getCancerAnswerDetails("Health", mpro_txn_id,
					"mpro");
			model.addAttribute("healthAnswerSurveyMap", healthAnswerSurveyMap);

			// Added By RK01318 so that previous of health-1 will work ends

			MProBuyerDetailsDTO mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			List<MProBuyerDetailsDTO> questionList = new ArrayList<MProBuyerDetailsDTO>();
			String H4 = request.getParameter("H4");
			String A_H4 = request.getParameter("A_H4");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H4);
			mProBuyerDetailsDTO.setANSWER(A_H4);
			// ADDED BY RK01318 STARTS
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {

				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS

			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H4");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H4)) {
				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H4A = request.getParameter("H4A");

				String A_H4A = request.getParameter("A_H4A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H4A);
				if (A_H4A != null && "on".equals(A_H4A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H4A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H4");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H4B = request.getParameter("H4B");
				String A_H4B = request.getParameter("A_H4B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H4B);
				if (A_H4B != null && "on".equals(A_H4B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");

				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H4B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H4");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H4C = request.getParameter("H4C");
				String A_H4C = request.getParameter("A_H4C");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H4C);
				if (A_H4C != null && "on".equals(A_H4C))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H4C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H4");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H4D = request.getParameter("H4D");
				String aH4D = (request.getParameter("A_H4D") == null || "".equals(request.getParameter("A_H4D"))) ? "NA"
						: request.getParameter("A_H4D"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H4D);
				mProBuyerDetailsDTO.setANSWER(aH4D);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H4D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H4");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H5 = request.getParameter("H5");
			String A_H5 = request.getParameter("A_H5");

			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H5);
			mProBuyerDetailsDTO.setANSWER(A_H5);
			// ADDED BY RK01318 STARTS
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H5");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H5)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H5A = request.getParameter("H5A");
				String A_H5A = request.getParameter("A_H5A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H5A);
				if (A_H5A != null && "on".equals(A_H5A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H5A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H5");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H5B = request.getParameter("H5B");
				String A_H5B = request.getParameter("A_H5B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H5B);
				if (A_H5B != null && "on".equals(A_H5B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H5B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H5");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H5C = request.getParameter("H5C");
				String A_H5C = request.getParameter("A_H5C");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H5C);
				if (A_H5C != null && "on".equals(A_H5C))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H5C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H5");
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H5D = request.getParameter("H5D");
				String A_H5D = request.getParameter("A_H5D");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H5D);
				if (A_H5D != null && "on".equals(A_H5D))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H5D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H5");
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H5E = request.getParameter("H5E");
				String aH5E = (request.getParameter("A_H5E") == null || "".equals(request.getParameter("A_H5E"))) ? "NA"
						: request.getParameter("A_H5E"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H5E);
				mProBuyerDetailsDTO.setANSWER(aH5E);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H5E");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H5");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();

			String H6 = request.getParameter("H6");
			String A_H6 = request.getParameter("A_H6");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H6);
			mProBuyerDetailsDTO.setANSWER(A_H6);
			// ADDED BY RK01318 STARTS
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H6");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H6)) {
				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H6A = request.getParameter("H6A");
				String A_H6A = request.getParameter("A_H6A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H6A);
				if (A_H6A != null && "on".equals(A_H6A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H6A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H6");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H6B = request.getParameter("H6B");
				String A_H6B = request.getParameter("A_H6B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H6B);
				if (A_H6B != null && "on".equals(A_H6B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");

				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H6B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H6");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H6C = request.getParameter("H6C");
				String aH6C = (request.getParameter("A_H6C") == null || "".equals(request.getParameter("A_H6C"))) ? "NA"
						: request.getParameter("A_H6C"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H6C);
				mProBuyerDetailsDTO.setANSWER(aH6C);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H6C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H6");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}

			if ((!isQuestionAndAnswerNull || "true".equals(isPrev)) && !"true".equals(forward)) {
				// ADDED BY RK01318 7-March-2019 STARTS
				APIErrorDTO errordto = new APIErrorDTO();
				model.addAttribute("error", errordto);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			if (!"true".equals(isPrev) && !isQuestionAndAnswerNull) {
				if ("true".equals(axispolicyOrNot)) {
					mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
					preissuanceService.saveMproBuyerDetails(questionList, mpro_txn_id, Constants.HEALTH);
				} else {
					preissuanceService.saveMproBuyerDetails(questionList, null, Constants.HEALTH);
				}

				attemptedQstns = attemptedQstns + 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);

			} else if (!"true".equals(directUrl)) {

				attemptedQstns = attemptedQstns - 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			}
		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured saveMproBuyerHealthAns2 : " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("mPro Un Authorized Access Throwing customer to error page");
		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns2() : END");
		if (isQuestionAndAnswerNull) {
			return "forward:/health-1?isPrev=true&forward=true";
		}
		return preissuancejsp + "health-3";
	}

	@RequestMapping(value = "/health-3", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerHealthAns3(Model model, HttpServletRequest request) {
		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}
		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns3() : START");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		String directUrl = request.getParameter("directUrl");
		boolean isQuestionAndAnswerNull = false;
		String appSource = (String) session.getAttribute("appSource");
		String mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
		try {
			String isPrev = request.getParameter("isPrev");
			// @RK01318 Starts
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";

			if ("true".equals(axispolicyOrNot)) {
				mpro_txn_id = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH, mpro_txn_id);
			}

			// @RK01318 Ends
			ThreadContext.push("health-3 section :" + mpro_txn_id);
			String category = "Health";
			Date currentDate = new java.util.Date();
			String createdBy = "mpro";
			String forward = request.getParameter("forward");

			// Added By RK01318 so that previous of health-1 will work starts
			String platform = request.getParameter("platform");
			String userAgent = request.getParameter("userAgent");
			Map<String, String> healthAnswerSurveyMap = preissuanceService.getCancerAnswerDetails("Health", mpro_txn_id,
					"mpro");
			model.addAttribute("healthAnswerSurveyMap", healthAnswerSurveyMap);
			// Added By RK01318 so that previous of health-1 will work ends
			MProBuyerDetailsDTO mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			List<MProBuyerDetailsDTO> questionList = new ArrayList<MProBuyerDetailsDTO>();

			String H7 = request.getParameter("H7");
			String A_H7 = request.getParameter("A_H7");

			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H7);
			mProBuyerDetailsDTO.setANSWER(A_H7);
			// ADDED BY RK01318 STARTS
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS

			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H7");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H7)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H7A = request.getParameter("H7A");
				String A_H7A = request.getParameter("A_H7A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H7A);
				if (A_H7A != null && "on".equals(A_H7A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H7A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H7");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H7B = request.getParameter("H7B");
				String A_H7B = request.getParameter("A_H7B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H7B);
				if (A_H7B != null && "on".equals(A_H7B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H7B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H7");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H7C = request.getParameter("H7C");
				String A_H7C = request.getParameter("A_H7C");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H7C);
				if (A_H7C != null && "on".equals(A_H7C))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H7C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H7");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H7D = request.getParameter("H7D");
				String aH7D = (request.getParameter("A_H7D") == null || "".equals(request.getParameter("A_H7D"))) ? "NA"
						: request.getParameter("A_H7D"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H7D);
				mProBuyerDetailsDTO.setANSWER(aH7D);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H7D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H7");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H8 = request.getParameter("H8");
			String A_H8 = request.getParameter("A_H8");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H8);
			mProBuyerDetailsDTO.setANSWER(A_H8);
			// ADDED BY RK01318 STARTS
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 END
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H8");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H8)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H8A = request.getParameter("H8A");
				String aH8A = (request.getParameter("A_H8A") == null || "".equals(request.getParameter("A_H8A"))) ? "NA"
						: request.getParameter("A_H8A"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H8A);
				mProBuyerDetailsDTO.setANSWER(aH8A);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H8A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H8");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H9 = request.getParameter("H9");
			String A_H9 = request.getParameter("A_H9");

			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H9);
			mProBuyerDetailsDTO.setANSWER(A_H9);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H9");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H9)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H9A = request.getParameter("H9A");
				String A_H9A = request.getParameter("A_H9A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H9A);
				if (A_H9A != null && "on".equals(A_H9A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H9A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H9");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H9B = request.getParameter("H9B");
				String A_H9B = request.getParameter("A_H9B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H9B);
				if (A_H9B != null && "on".equals(A_H9B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H9B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H9");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H9C = request.getParameter("H9C");
				String A_H9C = request.getParameter("A_H9C");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H9C);
				if (A_H9C != null && "on".equals(A_H9C))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H9C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H9");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H9D = request.getParameter("H9D");
				String A_H9D = request.getParameter("A_H9D");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H9D);
				if (A_H9D != null && "on".equals(A_H9D))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H9D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H9");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H9E = request.getParameter("H9E");
				String A_H9E = request.getParameter("A_H9E");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H9E);
				if (A_H9E != null && "on".equals(A_H9E))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H9E");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H9");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H9F = request.getParameter("H9F");
				String aH9F = (request.getParameter("A_H9F") == null || "".equals(request.getParameter("A_H9F"))) ? "NA"
						: request.getParameter("A_H9F"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H9F);
				mProBuyerDetailsDTO.setANSWER(aH9F);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H9F");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H9");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
			}

			if ((!isQuestionAndAnswerNull || "true".equals(isPrev)) && !("true".equals(forward))) {
				// ADDED BY RK01318 7-March-2019 STARTS
				APIErrorDTO errordto = new APIErrorDTO();
				model.addAttribute("error", errordto);
				// ADDED BY RK01318 7-March-2019 ENDS
			}

			if (!"true".equals(isPrev) && !isQuestionAndAnswerNull) {
				if ("true".equals(axispolicyOrNot)) {
					mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
					preissuanceService.saveMproBuyerDetails(questionList, mpro_txn_id, Constants.HEALTH);
				} else {
					preissuanceService.saveMproBuyerDetails(questionList, null, Constants.HEALTH);
				}

				attemptedQstns = attemptedQstns + 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			} else if (!"true".equals(directUrl)) {
				attemptedQstns = attemptedQstns - 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);

				String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");
				String nextJsp = preissuanceService.getNextJspPage(jspSequence, "/health-1", appSource);
				if (nextJsp.contains("psm")) {
					attemptedQstns = attemptedQstns - 2;
				} else if (nextJsp.contains("repsales")) {
					attemptedQstns = attemptedQstns - 1;
				}

				session.setAttribute("AttemptedQstns", attemptedQstns);
			}
		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured :saveMproBuyerHealthAns3 " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("mPro Un Authorized Access Throwing customer to error page");
		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns3() : END");
		if (isQuestionAndAnswerNull) {
			return "forward:/health-2?isPrev=true&forward=true";
		} else {
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";
			if ("true".equals(axispolicyOrNot)) {
				mpro_txn_id = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH, mpro_txn_id);
			}
			Map<String, PiSellerTxnDto> sellerDtoMap = preissuanceDao.getMproSellerInfoByTxnId(mpro_txn_id, null,
					appSource);
			for (Map.Entry<String, PiSellerTxnDto> sellerDto : sellerDtoMap.entrySet()) {
				String isSmoker = sellerDto.getValue().getIsSmoker();
				model.addAttribute("isSmoker", isSmoker);
			}
		}
		return preissuancejsp + "health-4";
	}

	@RequestMapping(value = "/health-4", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerHealthAns4(Model model, HttpServletRequest request) {
		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}
		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns4() : START");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		boolean isQuestionAndAnswerNull = false;
		String directUrl = request.getParameter("directUrl");
		String redirectionPath = preissuancejsp;
		String appSource = (String) session.getAttribute("appSource");
		String isPrev = request.getParameter("isPrev");
		try {

			String mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
			// @RK01318 Starts
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";

			if ("true".equals(axispolicyOrNot)) {
				mpro_txn_id = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH, mpro_txn_id);
			}

			// @RK01318 Ends
			ThreadContext.push("health-4 section :" + mpro_txn_id);
			String category = "Health";
			Date currentDate = new java.util.Date();
			String createdBy = "mpro";
			String forward = request.getParameter("forward");

			// Added By RK01318 so that previous of health-1 will work starts
			String platform = request.getParameter("platform");
			String userAgent = request.getParameter("userAgent");
			Map<String, String> healthAnswerSurveyMap = preissuanceService.getCancerAnswerDetails("Health", mpro_txn_id,
					"mpro");
			model.addAttribute("healthAnswerSurveyMap", healthAnswerSurveyMap);
			// Added By RK01318 so that previous of health-1 will work ends

			MProBuyerDetailsDTO mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			List<MProBuyerDetailsDTO> questionList = new ArrayList<MProBuyerDetailsDTO>();

			String H10 = request.getParameter("H10");
			String A_H10 = request.getParameter("A_H10");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H10);
			mProBuyerDetailsDTO.setANSWER(A_H10);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H10");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H10)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H10A = request.getParameter("H10A");
				String A_H10A = request.getParameter("A_H10A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H10A);
				if (A_H10A != null && "on".equals(A_H10A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H10A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H10");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H10B = request.getParameter("H10B");
				String A_H10B = request.getParameter("A_H10B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H10B);
				if (A_H10B != null && "on".equals(A_H10B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H10B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H10");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H10C = request.getParameter("H10C");
				String A_H10C = request.getParameter("A_H10C");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H10C);
				if (A_H10C != null && "on".equals(A_H10C))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H10C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H10");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H10D = request.getParameter("H10D");
				String A_H10D = request.getParameter("A_H10D");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H10D);
				if (A_H10D != null && "on".equals(A_H10D))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H10D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H10");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H10E = request.getParameter("H10E");
				String A_H10E = request.getParameter("A_H10E");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H10E);
				if (A_H10E != null && "on".equals(A_H10E))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H10E");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H10");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H10F = request.getParameter("H10F");
				String A_H10F = request.getParameter("A_H10F");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H10F);
				if (A_H10F != null && "on".equals(A_H10F))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H10F");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H10");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H10G = request.getParameter("H10G");
				String aH10G = (request.getParameter("A_H10G") == null || "".equals(request.getParameter("A_H10G")))
						? "NA" : request.getParameter("A_H10G"); // Added by
																	// shubham
																	// 24-Jan-19
																	// to null
																	// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H10G);
				mProBuyerDetailsDTO.setANSWER(aH10G);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H10G");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H10");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H11 = request.getParameter("H11");
			String A_H11 = request.getParameter("A_H11");

			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H11);
			mProBuyerDetailsDTO.setANSWER(A_H11);
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H11");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H11)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11A = request.getParameter("H11A");
				String A_H11A = request.getParameter("A_H11A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11A);
				if (A_H11A != null && "on".equals(A_H11A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11B = request.getParameter("H11B");
				String A_H11B = request.getParameter("A_H11B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11B);
				if (A_H11B != null && "on".equals(A_H11B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11C = request.getParameter("H11C");
				String A_H11C = request.getParameter("A_H11C");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11C);
				if (A_H11C != null && "on".equals(A_H11C))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11D = request.getParameter("H11D");
				String A_H11D = request.getParameter("A_H11D");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11D);
				if (A_H11D != null && "on".equals(A_H11D))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11E = request.getParameter("H11E");
				String A_H11E = request.getParameter("A_H11E");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11E);
				if (A_H11E != null && "on".equals(A_H11E))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11E");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11F = request.getParameter("H11F");
				String A_H11F = request.getParameter("A_H11F");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11F);
				if (A_H11F != null && "on".equals(A_H11F))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11F");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11G = request.getParameter("H11G");
				String A_H11G = request.getParameter("A_H11G");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11G);
				if (A_H11G != null && "on".equals(A_H11G))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11G");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11H = request.getParameter("H11H");
				String A_H11H = request.getParameter("A_H11H");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11H);
				if (A_H11H != null && "on".equals(A_H11H))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11H");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11I = request.getParameter("H11I");
				String A_H11I = request.getParameter("A_H11I");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11I);
				if (A_H11I != null && "on".equals(A_H11I))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11I");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11J = request.getParameter("H11J");
				String A_H11J = request.getParameter("A_H11J");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11J);
				if (A_H11J != null && "on".equals(A_H11J))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11J");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11K = request.getParameter("H11K");
				String A_H11K = request.getParameter("A_H11K");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11K);
				if (A_H11K != null && "on".equals(A_H11K))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11K");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11L = request.getParameter("H11L");
				String A_H11L = request.getParameter("A_H11L");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11L);
				if (A_H11L != null && "on".equals(A_H11L))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11L");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11M = request.getParameter("H11M");
				String A_H11M = request.getParameter("A_H11M");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11M);
				if (A_H11M != null && "on".equals(A_H11M))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11M");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H11N = request.getParameter("H11N");
				String aH11N = (request.getParameter("A_H11N") == null || "".equals(request.getParameter("A_H11N")))
						? "NA" : request.getParameter("A_H11N"); // Added by
																	// shubham
																	// 24-Jan-19
																	// to null
																	// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H11N);
				mProBuyerDetailsDTO.setANSWER(aH11N);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H11N");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H11");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

			}
			String A_H12 = null;
			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H12 = request.getParameter("H12");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H12);
			mProBuyerDetailsDTO.setANSWER(A_H12);
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H12");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			MProBuyerDetailsDTO toGetIndex = mProBuyerDetailsDTO;
			questionList.add(mProBuyerDetailsDTO);

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H12A = request.getParameter("H12A");
			String A_H12A = request.getParameter("A_H12A");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H12A);
			mProBuyerDetailsDTO.setANSWER(A_H12A);
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS

			}
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H12A");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("H12");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H12A)) {
				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H12Ai = request.getParameter("H12Ai");
				String A_H12Ai = request.getParameter("A_H12Ai");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H12Ai);
				mProBuyerDetailsDTO.setANSWER(A_H12Ai);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H12Ai");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Reflexive");
				mProBuyerDetailsDTO.setAnswerType("boolean");
				mProBuyerDetailsDTO.setParentId("NA");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H12B = request.getParameter("H12B");
			String A_H12B = request.getParameter("A_H12B");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H12B);
			mProBuyerDetailsDTO.setANSWER(A_H12B);
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS

			}
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H12B");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("H12");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H12B)) {
				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H12Bi = request.getParameter("H12Bi");
				String A_H12Bi = request.getParameter("A_H12Bi");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H12Bi);
				mProBuyerDetailsDTO.setANSWER(A_H12Bi);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H12Bi");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("boolean");
				mProBuyerDetailsDTO.setParentId("H12B");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H12Bii = request.getParameter("H12Bii");
				String A_H12Bii = request.getParameter("A_H12Bii");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H12Bii);
				mProBuyerDetailsDTO.setANSWER(A_H12Bii);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H12Bii");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("boolean");
				mProBuyerDetailsDTO.setParentId("H12B");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H12C = request.getParameter("H12C");
			String A_H12C = request.getParameter("A_H12C");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H12C);
			mProBuyerDetailsDTO.setANSWER(A_H12C);
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);

			}
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H12C");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("H12");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H12A) || "Y".equalsIgnoreCase(A_H12B) || "Y".equalsIgnoreCase(A_H12C)) {
				A_H12 = "Y";
				int index = questionList.indexOf(toGetIndex);
				questionList.get(index).setANSWER(A_H12);
			} else {
				A_H12 = "N";
				int index = questionList.indexOf(toGetIndex);
				questionList.get(index).setANSWER(A_H12);
			}

			if ((!isQuestionAndAnswerNull || "true".equals(isPrev)) && !("true".equals(forward))) {
				// ADDED BY RK01318 7-March-2019 STARTS
				APIErrorDTO errordto = new APIErrorDTO();
				model.addAttribute("error", errordto);
				// ADDED BY RK01318 7-March-2019 ENDS
			}

			if (!("true".equals(isPrev)) && !isQuestionAndAnswerNull) {
				if ("true".equals(axispolicyOrNot)) {
					mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
					preissuanceService.saveMproBuyerDetails(questionList, mpro_txn_id, Constants.HEALTH);
				} else {
					preissuanceService.saveMproBuyerDetails(questionList, null, Constants.HEALTH);
				}

				attemptedQstns = attemptedQstns + 5;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			} else if (!"true".equals(directUrl)) {
				String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");

				String isWopInsured = session.getAttribute("isWopInsured") + "";
				if ("true".equals(isWopInsured)) {
					attemptedQstns = attemptedQstns - 3;
				} else {

					String nextJsp = preissuanceService.getNextJspPage(jspSequence, "/health-1", appSource);

					if (nextJsp.contains("psm")) {
						attemptedQstns = attemptedQstns - 3;
					} else if (nextJsp.contains("repsales")) {
						attemptedQstns = attemptedQstns - 1;
					}

				}
				session.setAttribute("AttemptedQstns", attemptedQstns);
			}
			String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");
			if (jspSequence != null) {

				// @RK01318 Starts
				boolean isWopInsured = preissuanceService.isInsurerApplicable(mpro_txn_id);

				if (isWopInsured) {
					return preissuancejsp + "insurerhealth-1";
				}
				// @RK01318 Ends
				String nextJspPage = preissuanceService.getNextJspPage(jspSequence, "/health-1", appSource);
				redirectionPath = preissuancejsp + nextJspPage;
				if ("/otp".equalsIgnoreCase(nextJspPage)) {
					String agtChannelCode = session.getAttribute("AgtChannelCode") + "";
					if ("X".equalsIgnoreCase(agtChannelCode)) {
						redirectionPath = preissuancejsp + "verify";
					}
				}
				if (redirectionPath.contains("psm")) {
					Map<String, String> answerMap = preissuanceService.getCancerAnswerDetails("Psm", mpro_txn_id,
							createdBy);
					model.addAttribute("psmSurveyAnswerMap", answerMap);
				}
			} else {
				redirectionPath = preissuancejsp + "mproErrorPage";
			}

		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured : saveMproBuyerHealthAns6", ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("mPro Un Authorized Access Throwing customer to error page");
		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns6() : END");
		// ADDED BY RK01318 STARTS
		if (isQuestionAndAnswerNull) {
			return "forward:/health-3?isPrev=true&forward=true";
		}
		// ADDED BY RK01318 ENDS
		return redirectionPath;
	}

	@RequestMapping(value = "/insurerhealth-1", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerInsurerHealthAns1(Model model, HttpServletRequest request) {
		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}
		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns1() : START");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		boolean isQuestionAndAnswerNull = false;// ADDED BY RK01318
		String isPrev = request.getParameter("isPrev");// ADDED BY RK01318
		try {
			String mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
			ThreadContext.push("health-1 section :" + mpro_txn_id);
			String directUrl = request.getParameter("directUrl");
			String category = "Health";
			Date currentDate = new java.util.Date();
			String createdBy = "mpro";

			// @RK01318 Starts
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";

			if ("true".equals(axispolicyOrNot)) {
				mpro_txn_id = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH, mpro_txn_id);
			}

			// @RK01318 Ends

			// Added By RK01318 so that previous of health-1 will work starts
			String platform = request.getParameter("platform");
			String userAgent = request.getParameter("userAgent");
			String forward = request.getParameter("forward");
			Map<String, String> healthAnswerSurveyMap = preissuanceService.getCancerAnswerDetails("Health", mpro_txn_id,
					"mpro");
			model.addAttribute("healthAnswerSurveyMap", healthAnswerSurveyMap);
			// Added By RK01318 so that previous of health-1 will work ends

			MProBuyerDetailsDTO mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			List<MProBuyerDetailsDTO> questionList = new ArrayList<MProBuyerDetailsDTO>();

			String H25 = request.getParameter("H25");
			String p_a_1 = request.getParameter("p_1");

			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H25);
			mProBuyerDetailsDTO.setANSWER(p_a_1);
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H25");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);

			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(p_a_1)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H25A = request.getParameter("H25A");
				String ra1 = (request.getParameter("r_a_1") == null || "".equals(request.getParameter("r_a_1"))) ? "NA"
						: request.getParameter("r_a_1"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H25A);
				mProBuyerDetailsDTO.setANSWER(ra1);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H25A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H25");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H25B = request.getParameter("H25B");
				String ra2 = (request.getParameter("r_a_2") == null || "".equals(request.getParameter("r_a_2"))) ? "NA"
						: request.getParameter("r_a_2"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H25B);
				mProBuyerDetailsDTO.setANSWER(ra2);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H25B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H25");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H25C = request.getParameter("H25C");
				String r_a_3 = request.getParameter("a_3");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H25C);
				mProBuyerDetailsDTO.setANSWER(r_a_3);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H25C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("boolean");
				mProBuyerDetailsDTO.setParentId("H25");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H25D = request.getParameter("H25D");
				String ra4 = (request.getParameter("r_a_4") == null || "".equals(request.getParameter("r_a_4"))) ? "NA"
						: request.getParameter("r_a_4"); // Added by shubham
															// 24-Jan-19 to null
															// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H25D);
				mProBuyerDetailsDTO.setANSWER(ra4);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H25D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H25");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}
			// Question H2 starts

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H26 = request.getParameter("H26");
			String a_h26 = request.getParameter("A_h26");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H26);
			mProBuyerDetailsDTO.setANSWER(a_h26);
			// RK01318 STARTS

			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H26");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(a_h26)) {
				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H26A = request.getParameter("H26A");
				String a_h26a = request.getParameter("a_h26a");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H26A);
				if (a_h26a != null && "on".equals(a_h26a))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H26A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H26");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H26B = request.getParameter("H26B");
				String a_h26b = request.getParameter("a_h26b");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H26B);
				if (a_h26b != null && "on".equals(a_h26b))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");

				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H26B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H26");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H26C = request.getParameter("H26C");
				String a_h26c = request.getParameter("a_h26c");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H26C);
				if (a_h26c != null && "on".equals(a_h26c))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H26C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H26");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H2D = request.getParameter("H26D");
				String ah2d = (request.getParameter("a_h26d") == null || "".equals(request.getParameter("a_h26d")))
						? "NA" : request.getParameter("a_h26d"); // Added by
																	// shubham
																	// 24-Jan-19
																	// to null
																	// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H2D);
				mProBuyerDetailsDTO.setANSWER(ah2d);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H26D");
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H26");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}

			// Question 3 starts @Added by RR1517
			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H27 = request.getParameter("H27");
			String A_H27 = request.getParameter("A_H27");

			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H27);
			mProBuyerDetailsDTO.setANSWER(A_H27);
			// ADDED BY RK01318 STARTS
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H27");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H27)) {
				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H27A = request.getParameter("H27A");
				String A_H27A = request.getParameter("A_H27A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H27A);
				mProBuyerDetailsDTO.setANSWER(A_H27A);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H27A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("boolean");
				mProBuyerDetailsDTO.setParentId("H27");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H27B = request.getParameter("H27B");
				String aH27B = (request.getParameter("A_H27B") == null || "".equals(request.getParameter("A_H27B")))
						? "NA" : request.getParameter("A_H27B"); // Added by
																	// shubham
																	// 24-Jan-19
																	// to null
																	// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H27B);
				mProBuyerDetailsDTO.setANSWER(aH27B);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H27B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H27");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}

			// Question 3 ends @Added by RR1517
			if ((!isQuestionAndAnswerNull || "true".equals(isPrev)) && !"true".equals(forward)) {
				// ADDED BY RK01318 7-March-2019 STARTS
				APIErrorDTO errordto = new APIErrorDTO();
				model.addAttribute("error", errordto);
				// ADDED BY RK01318 7-March-2019 ENDS
			}

			if (!"true".equals(isPrev) && !isQuestionAndAnswerNull) {
				if ("true".equals(axispolicyOrNot)) {

					mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
					preissuanceService.saveMproBuyerDetails(questionList, mpro_txn_id, Constants.HEALTH);
				} else {
					preissuanceService.saveMproBuyerDetails(questionList, null, Constants.HEALTH);
				}

				attemptedQstns = attemptedQstns + 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			} else if (!"true".equals(directUrl)) {

				attemptedQstns = attemptedQstns - 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			}
		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured saveMproBuyerHealthAns1 : " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("mPro Un Authorized Access Throwing customer to error page");
		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns1() : END");
		if (isQuestionAndAnswerNull) {
			return "forward:/health-4?isPrev=true&forward=true";
		} else {
			return preissuancejsp + "insurerhealth-2";
		}
	}

	@RequestMapping(value = "/insurerhealth-2", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerInsurerHealthAns2(Model model, HttpServletRequest request) {
		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}

		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns2() : START");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		boolean isQuestionAndAnswerNull = false;
		String directUrl = request.getParameter("directUrl");
		String isPrev = request.getParameter("isPrev");
		try {

			String mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
			// @RK01318 Starts
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";

			if ("true".equals(axispolicyOrNot)) {
				mpro_txn_id = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH, mpro_txn_id);
			}

			// @RK01318 Ends
			ThreadContext.push("healt-2 section :" + mpro_txn_id);
			String category = "Health";
			Date currentDate = new java.util.Date();
			String createdBy = "mpro";
			String forward = request.getParameter("forward");

			String platform = request.getParameter("platform");
			String userAgent = request.getParameter("userAgent");

			// Added By RK01318 so that previous of health-1 will work starts
			Map<String, String> healthAnswerSurveyMap = preissuanceService.getCancerAnswerDetails("Health", mpro_txn_id,
					"mpro");
			model.addAttribute("healthAnswerSurveyMap", healthAnswerSurveyMap);

			// Added By RK01318 so that previous of health-1 will work ends

			MProBuyerDetailsDTO mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			List<MProBuyerDetailsDTO> questionList = new ArrayList<MProBuyerDetailsDTO>();
			String H28 = request.getParameter("H28");
			String A_H28 = request.getParameter("A_H28");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H28);
			mProBuyerDetailsDTO.setANSWER(A_H28);
			// ADDED BY RK01318 STARTS
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {

				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS

			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H28");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H28)) {
				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H28A = request.getParameter("H28A");

				String A_H28A = request.getParameter("A_H28A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H28A);
				if (A_H28A != null && "on".equals(A_H28A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H28A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H28");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H28B = request.getParameter("H28B");
				String A_H28B = request.getParameter("A_H28B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H28B);
				if (A_H28B != null && "on".equals(A_H28B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");

				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H4B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H4");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H28C = request.getParameter("H28C");
				String A_H28C = request.getParameter("A_H28C");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H28C);
				if (A_H28C != null && "on".equals(A_H28C))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H28C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H28");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H28D = request.getParameter("H28D");
				String aH28D = (request.getParameter("A_H28D") == null || "".equals(request.getParameter("A_H28D")))
						? "NA" : request.getParameter("A_H28D"); // Added by
																	// shubham
																	// 24-Jan-19
																	// to null
																	// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H28D);
				mProBuyerDetailsDTO.setANSWER(aH28D);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H28D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H28");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H29 = request.getParameter("H29");
			String A_H29 = request.getParameter("A_H29");

			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H29);
			mProBuyerDetailsDTO.setANSWER(A_H29);
			// ADDED BY RK01318 STARTS
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H29");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H29)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H29A = request.getParameter("H29A");
				String A_H29A = request.getParameter("A_H29A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H29A);
				if (A_H29A != null && "on".equals(A_H29A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H29A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H29");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H29B = request.getParameter("H29B");
				String A_H29B = request.getParameter("A_H29B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H29B);
				if (A_H29B != null && "on".equals(A_H29B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H29B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H29");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H29C = request.getParameter("H29C");
				String A_H29C = request.getParameter("A_H29C");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H29C);
				if (A_H29C != null && "on".equals(A_H29C))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H29C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H29");
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H29D = request.getParameter("H29D");
				String A_H29D = request.getParameter("A_H29D");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H29D);
				if (A_H29D != null && "on".equals(A_H29D))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H29D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H29");
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H29E = request.getParameter("H29E");
				String aH29E = (request.getParameter("A_H29E") == null || "".equals(request.getParameter("A_H29E")))
						? "NA" : request.getParameter("A_H29E"); // Added by
																	// shubham
																	// 24-Jan-19
																	// to null
																	// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H29E);
				mProBuyerDetailsDTO.setANSWER(aH29E);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H29E");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H29");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();

			String H30 = request.getParameter("H30");
			String A_H30 = request.getParameter("A_H30");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H30);
			mProBuyerDetailsDTO.setANSWER(A_H30);
			// ADDED BY RK01318 STARTS
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H30");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H30)) {
				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H30A = request.getParameter("H30A");
				String A_H30A = request.getParameter("A_H30A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H30A);
				if (A_H30A != null && "on".equals(A_H30A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H30A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H30");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H30B = request.getParameter("H30B");
				String A_H30B = request.getParameter("A_H30B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H30B);
				if (A_H30B != null && "on".equals(A_H30B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");

				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H30B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H30");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H30C = request.getParameter("H30C");
				String aH30C = (request.getParameter("A_H30C") == null || "".equals(request.getParameter("A_H30C")))
						? "NA" : request.getParameter("A_H30C"); // Added by
																	// shubham
																	// 24-Jan-19
																	// to null
																	// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H30C);
				mProBuyerDetailsDTO.setANSWER(aH30C);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H30C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H30");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}

			if ((!isQuestionAndAnswerNull || "true".equals(isPrev)) && !"true".equals(forward)) {
				// ADDED BY RK01318 7-March-2019 STARTS
				APIErrorDTO errordto = new APIErrorDTO();
				model.addAttribute("error", errordto);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			if (!"true".equals(isPrev) && !isQuestionAndAnswerNull) {
				if ("true".equals(axispolicyOrNot)) {
					mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
					preissuanceService.saveMproBuyerDetails(questionList, mpro_txn_id, Constants.HEALTH);
				} else {
					preissuanceService.saveMproBuyerDetails(questionList, null, Constants.HEALTH);
				}

				attemptedQstns = attemptedQstns + 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);

			} else if (!"true".equals(directUrl)) {

				attemptedQstns = attemptedQstns - 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			}
		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured saveMproBuyerHealthAns2 : " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("mPro Un Authorized Access Throwing customer to error page");
		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns2() : END");
		if (isQuestionAndAnswerNull) {
			return "forward:/insurerhealth-1?isPrev=true&forward=true";
		}
		return preissuancejsp + "insurerhealth-3";
	}

	@RequestMapping(value = "/insurerhealth-3", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerInsurerHealthAns3(Model model, HttpServletRequest request) {
		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}
		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns3() : START");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		String directUrl = request.getParameter("directUrl");
		boolean isQuestionAndAnswerNull = false;
		String appSource = (String) session.getAttribute("appSource");
		String mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
		try {
			String isPrev = request.getParameter("isPrev");
			// @RK01318 Starts
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";

			if ("true".equals(axispolicyOrNot)) {
				mpro_txn_id = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH, mpro_txn_id);
			}

			// @RK01318 Ends
			ThreadContext.push("health-3 section :" + mpro_txn_id);
			String category = "Health";
			Date currentDate = new java.util.Date();
			String createdBy = "mpro";
			String forward = request.getParameter("forward");

			// Added By RK01318 so that previous of health-1 will work starts
			String platform = request.getParameter("platform");
			String userAgent = request.getParameter("userAgent");
			Map<String, String> healthAnswerSurveyMap = preissuanceService.getCancerAnswerDetails("Health", mpro_txn_id,
					"mpro");
			model.addAttribute("healthAnswerSurveyMap", healthAnswerSurveyMap);
			// Added By RK01318 so that previous of health-1 will work ends
			MProBuyerDetailsDTO mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			List<MProBuyerDetailsDTO> questionList = new ArrayList<MProBuyerDetailsDTO>();

			String H31 = request.getParameter("H31");
			String A_H31 = request.getParameter("A_H31");

			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H31);
			mProBuyerDetailsDTO.setANSWER(A_H31);
			// ADDED BY RK01318 STARTS
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS

			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H31");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H31)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H31A = request.getParameter("H31A");
				String A_H31A = request.getParameter("A_H31A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H31A);
				if (A_H31A != null && "on".equals(A_H31A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H31A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H31");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H31B = request.getParameter("H31B");
				String A_H31B = request.getParameter("A_H31B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H31B);
				if (A_H31B != null && "on".equals(A_H31B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H31B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H31");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H31C = request.getParameter("H31C");
				String A_H31C = request.getParameter("A_H31C");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H31C);
				if (A_H31C != null && "on".equals(A_H31C))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H31C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H31");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H31D = request.getParameter("H31D");
				String aH31D = (request.getParameter("A_H31D") == null || "".equals(request.getParameter("A_H31D")))
						? "NA" : request.getParameter("A_H31D"); // Added by
																	// shubham
																	// 24-Jan-19
																	// to null
																	// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H31D);
				mProBuyerDetailsDTO.setANSWER(aH31D);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H31D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H31");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H32 = request.getParameter("H32");
			String A_H32 = request.getParameter("A_H32");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H32);
			mProBuyerDetailsDTO.setANSWER(A_H32);
			// ADDED BY RK01318 STARTS
			if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 END
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H32");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setPlatform(platform);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H32)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H32A = request.getParameter("H32A");
				String aH32A = (request.getParameter("A_H32A") == null || "".equals(request.getParameter("A_H32A")))
						? "NA" : request.getParameter("A_H32A"); // Added by
																	// shubham
																	// 24-Jan-19
																	// to null
																	// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H32A);
				mProBuyerDetailsDTO.setANSWER(aH32A);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H32A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H32");
				mProBuyerDetailsDTO.setPlatform(platform);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H33 = request.getParameter("H33");
			String A_H33 = request.getParameter("A_H33");

			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H33);
			mProBuyerDetailsDTO.setANSWER(A_H33);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H33");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H33)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H33A = request.getParameter("H33A");
				String A_H33A = request.getParameter("A_H33A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H33A);
				if (A_H33A != null && "on".equals(A_H33A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H33A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H33");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H33B = request.getParameter("H33B");
				String A_H33B = request.getParameter("A_H33B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H33B);
				if (A_H33B != null && "on".equals(A_H33B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H33B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H33");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H33C = request.getParameter("H33C");
				String A_H33C = request.getParameter("A_H33C");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H33C);
				if (A_H33C != null && "on".equals(A_H33C))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H33C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H33");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H33D = request.getParameter("H33D");
				String A_H33D = request.getParameter("A_H33D");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H33D);
				if (A_H33D != null && "on".equals(A_H33D))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H33D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H33");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H33E = request.getParameter("H33E");
				String A_H33E = request.getParameter("A_H33E");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H33E);
				if (A_H33E != null && "on".equals(A_H33E))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H33E");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H33");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H33F = request.getParameter("H33F");
				String aH33F = (request.getParameter("A_H33F") == null || "".equals(request.getParameter("A_H33F")))
						? "NA" : request.getParameter("A_H33F"); // Added by
																	// shubham
																	// 24-Jan-19
																	// to null
																	// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H33F);
				mProBuyerDetailsDTO.setANSWER(aH33F);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H33F");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H33");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
			}

			if ((!isQuestionAndAnswerNull || "true".equals(isPrev)) && !("true".equals(forward))) {
				// ADDED BY RK01318 7-March-2019 STARTS
				APIErrorDTO errordto = new APIErrorDTO();
				model.addAttribute("error", errordto);
				// ADDED BY RK01318 7-March-2019 ENDS
			}

			if (!"true".equals(isPrev) && !isQuestionAndAnswerNull) {
				if ("true".equals(axispolicyOrNot)) {
					mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
					preissuanceService.saveMproBuyerDetails(questionList, mpro_txn_id, Constants.HEALTH);
				} else {
					preissuanceService.saveMproBuyerDetails(questionList, null, Constants.HEALTH);
				}

				attemptedQstns = attemptedQstns + 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			} else if (!"true".equals(directUrl)) {
				attemptedQstns = attemptedQstns - 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);

				String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");
				String nextJsp = preissuanceService.getNextJspPage(jspSequence, "/health-1", appSource);
				if (nextJsp.contains("psm")) {
					attemptedQstns = attemptedQstns - 2;
				} else if (nextJsp.contains("repsales")) {
					attemptedQstns = attemptedQstns - 1;
				}
				session.setAttribute("AttemptedQstns", attemptedQstns);
			}
		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured :saveMproBuyerHealthAns3 " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("mPro Un Authorized Access Throwing customer to error page");
		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns3() : END");
		if (isQuestionAndAnswerNull) {
			return "forward:/insurerhealth-2?isPrev=true&forward=true";
		} else {
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";
			if ("true".equals(axispolicyOrNot)) {
				mpro_txn_id = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH, mpro_txn_id);
			}
			Map<String, PiSellerTxnDto> sellerDtoMap = preissuanceDao.getMproSellerInfoByTxnId(mpro_txn_id, null,
					appSource);
			for (Map.Entry<String, PiSellerTxnDto> sellerDto : sellerDtoMap.entrySet()) {
				String isSmoker = sellerDto.getValue().getIsSmoker();
				model.addAttribute("isSmoker", isSmoker);
			}
		}
		return preissuancejsp + "insurerhealth-4";
	}

	@RequestMapping(value = "/insurerhealth-4", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerInsurerHealthAns4(Model model, HttpServletRequest request) {
		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}
		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns4() : START");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		boolean isQuestionAndAnswerNull = false;
		String directUrl = request.getParameter("directUrl");
		String redirectionPath = preissuancejsp;
		String appSource = (String) session.getAttribute("appSource");
		String isPrev = request.getParameter("isPrev");
		try {

			String mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
			// @RK01318 Starts
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";

			if ("true".equals(axispolicyOrNot)) {
				mpro_txn_id = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH, mpro_txn_id);
			}

			// @RK01318 Ends
			ThreadContext.push("health-4 section :" + mpro_txn_id);
			String category = "Health";
			Date currentDate = new java.util.Date();
			String createdBy = "mpro";
			String forward = request.getParameter("forward");

			// Added By RK01318 so that previous of health-1 will work starts
			String platform = request.getParameter("platform");
			String userAgent = request.getParameter("userAgent");
			Map<String, String> healthAnswerSurveyMap = preissuanceService.getCancerAnswerDetails("Health", mpro_txn_id,
					"mpro");
			model.addAttribute("healthAnswerSurveyMap", healthAnswerSurveyMap);
			// Added By RK01318 so that previous of health-1 will work ends

			MProBuyerDetailsDTO mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			List<MProBuyerDetailsDTO> questionList = new ArrayList<MProBuyerDetailsDTO>();

			String H34 = request.getParameter("H34");
			String A_H34 = request.getParameter("A_H34");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H34);
			mProBuyerDetailsDTO.setANSWER(A_H34);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H34");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H34)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H34A = request.getParameter("H34A");
				String A_H34A = request.getParameter("A_H34A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H34A);
				if (A_H34A != null && "on".equals(A_H34A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H34A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H34");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H34B = request.getParameter("H34B");
				String A_H34B = request.getParameter("A_H34B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H34B);
				if (A_H34B != null && "on".equals(A_H34B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H34B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H34");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H34C = request.getParameter("H34C");
				String A_H34C = request.getParameter("A_H34C");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H34C);
				if (A_H34C != null && "on".equals(A_H34C))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H34C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H34");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H34D = request.getParameter("H34D");
				String A_H34D = request.getParameter("A_H34D");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H34D);
				if (A_H34D != null && "on".equals(A_H34D))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H34D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H34");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H34E = request.getParameter("H34E");
				String A_H34E = request.getParameter("A_H34E");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H34E);
				if (A_H34E != null && "on".equals(A_H34E))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H34E");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H34");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H34F = request.getParameter("H34F");
				String A_H34F = request.getParameter("A_H34F");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H34F);
				if (A_H34F != null && "on".equals(A_H34F))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H34F");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H34");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H34G = request.getParameter("H34G");
				String aH34G = (request.getParameter("A_H34G") == null || "".equals(request.getParameter("A_H34G")))
						? "NA" : request.getParameter("A_H34G"); // Added by
																	// shubham
																	// 24-Jan-19
																	// to null
																	// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H34G);
				mProBuyerDetailsDTO.setANSWER(aH34G);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H34G");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H10");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H35 = request.getParameter("H35");
			String A_H35 = request.getParameter("A_H35");

			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H35);
			mProBuyerDetailsDTO.setANSWER(A_H35);
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H35");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H35)) {

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35A = request.getParameter("H35A");
				String A_H35A = request.getParameter("A_H35A");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35A);
				if (A_H35A != null && "on".equals(A_H35A))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35A");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35B = request.getParameter("H35B");
				String A_H35B = request.getParameter("A_H35B");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35B);
				if (A_H35B != null && "on".equals(A_H35B))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35B");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35C = request.getParameter("H35C");
				String A_H35C = request.getParameter("A_H35C");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35C);
				if (A_H35C != null && "on".equals(A_H35C))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35C");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35D = request.getParameter("H35D");
				String A_H35D = request.getParameter("A_H35D");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35D);
				if (A_H35D != null && "on".equals(A_H35D))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35D");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35E = request.getParameter("H35E");
				String A_H35E = request.getParameter("A_H35E");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35E);
				if (A_H35E != null && "on".equals(A_H35E))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35E");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35F = request.getParameter("H35F");
				String A_H35F = request.getParameter("A_H35F");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35F);
				if (A_H35F != null && "on".equals(A_H35F))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35F");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35G = request.getParameter("H35G");
				String A_H35G = request.getParameter("A_H35G");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35G);
				if (A_H35G != null && "on".equals(A_H35G))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35G");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35H = request.getParameter("H35H");
				String A_H35H = request.getParameter("A_H35H");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35H);
				if (A_H35H != null && "on".equals(A_H35H))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35H");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");

				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35I = request.getParameter("H35I");
				String A_H35I = request.getParameter("A_H35I");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35I);
				if (A_H35I != null && "on".equals(A_H35I))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35I");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35J = request.getParameter("H35J");
				String A_H35J = request.getParameter("A_H35J");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35J);
				if (A_H35J != null && "on".equals(A_H35J))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35J");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35K = request.getParameter("H35K");
				String A_H35K = request.getParameter("A_H35K");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35K);
				if (A_H35K != null && "on".equals(A_H35K))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35K");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35L = request.getParameter("H35L");
				String A_H35L = request.getParameter("A_H35L");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35L);
				if (A_H35L != null && "on".equals(A_H35L))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35L");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35M = request.getParameter("H35M");
				String A_H35M = request.getParameter("A_H35M");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35M);
				if (A_H35M != null && "on".equals(A_H35M))
					mProBuyerDetailsDTO.setANSWER("Y");
				else
					mProBuyerDetailsDTO.setANSWER("N");
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35M");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H35N = request.getParameter("H35N");
				String aH35N = (request.getParameter("A_H35N") == null || "".equals(request.getParameter("A_H35N")))
						? "NA" : request.getParameter("A_H35N"); // Added by
																	// shubham
																	// 24-Jan-19
																	// to null
																	// with NA
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H35N);
				mProBuyerDetailsDTO.setANSWER(aH35N);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H35N");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("string");
				mProBuyerDetailsDTO.setParentId("H35");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

			}
			String A_H36 = null;
			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H36 = request.getParameter("H36");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H36);
			mProBuyerDetailsDTO.setANSWER(A_H36);
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H36");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("NA");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			MProBuyerDetailsDTO toGetIndex = mProBuyerDetailsDTO;
			questionList.add(mProBuyerDetailsDTO);

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H36A = request.getParameter("H36A");
			String A_H36A = request.getParameter("A_H36A");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H36A);
			mProBuyerDetailsDTO.setANSWER(A_H36A);
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS

			}
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H36A");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("H36");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H36A)) {
				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H36Ai = request.getParameter("H36Ai");
				String A_H36Ai = request.getParameter("A_H36Ai");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H36Ai);
				mProBuyerDetailsDTO.setANSWER(A_H36Ai);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H36Ai");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Reflexive");
				mProBuyerDetailsDTO.setAnswerType("boolean");
				mProBuyerDetailsDTO.setParentId("NA");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H36B = request.getParameter("H36B");
			String A_H36B = request.getParameter("A_H36B");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H36B);
			mProBuyerDetailsDTO.setANSWER(A_H36B);
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS

			}
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H36B");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("H36");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H36B)) {
				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H36Bi = request.getParameter("H36Bi");
				String A_H36Bi = request.getParameter("A_H36Bi");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H36Bi);
				mProBuyerDetailsDTO.setANSWER(A_H36Bi);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H36Bi");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("boolean");
				mProBuyerDetailsDTO.setParentId("H36B");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

				mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
				String H36Bii = request.getParameter("H36Bii");
				String A_H36Bii = request.getParameter("A_H36Bii");
				mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
				mProBuyerDetailsDTO.setQSTN_NAME(H36Bii);
				mProBuyerDetailsDTO.setANSWER(A_H36Bii);
				mProBuyerDetailsDTO.setCREATED_DT(currentDate);
				mProBuyerDetailsDTO.setQSTN_CAT(category);
				mProBuyerDetailsDTO.setQSTN_ID("H36Bii");
				mProBuyerDetailsDTO.setCREATED_BY(createdBy);
				mProBuyerDetailsDTO.setQuestionType("Secondary");
				mProBuyerDetailsDTO.setAnswerType("boolean");
				mProBuyerDetailsDTO.setParentId("H36B");
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);

			}

			mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
			String H36C = request.getParameter("H36C");
			String A_H36C = request.getParameter("A_H36C");
			mProBuyerDetailsDTO.setTXNID(mpro_txn_id);
			mProBuyerDetailsDTO.setQSTN_NAME(H36C);
			mProBuyerDetailsDTO.setANSWER(A_H36C);
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);

			}
			mProBuyerDetailsDTO.setCREATED_DT(currentDate);
			mProBuyerDetailsDTO.setQSTN_CAT(category);
			mProBuyerDetailsDTO.setQSTN_ID("H36C");
			mProBuyerDetailsDTO.setCREATED_BY(createdBy);
			mProBuyerDetailsDTO.setQuestionType("Reflexive");
			mProBuyerDetailsDTO.setAnswerType("boolean");
			mProBuyerDetailsDTO.setParentId("H36");
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);

			if ("Y".equalsIgnoreCase(A_H36A) || "Y".equalsIgnoreCase(A_H36B) || "Y".equalsIgnoreCase(A_H36C)) {
				A_H36 = "Y";
				int index = questionList.indexOf(toGetIndex);
				questionList.get(index).setANSWER(A_H36);
			} else {
				A_H36 = "N";
				int index = questionList.indexOf(toGetIndex);
				questionList.get(index).setANSWER(A_H36);
			}

			if ((!isQuestionAndAnswerNull || "true".equals(isPrev)) && !("true".equals(forward))) {
				// ADDED BY RK01318 7-March-2019 STARTS
				APIErrorDTO errordto = new APIErrorDTO();
				model.addAttribute("error", errordto);
				// ADDED BY RK01318 7-March-2019 ENDS
			}

			if (!("true".equals(isPrev)) && !isQuestionAndAnswerNull) {
				if ("true".equals(axispolicyOrNot)) {
					mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
					preissuanceService.saveMproBuyerDetails(questionList, mpro_txn_id, Constants.HEALTH);
				} else {
					preissuanceService.saveMproBuyerDetails(questionList, null, Constants.HEALTH);
				}

				attemptedQstns = attemptedQstns + 5;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			} else if (!"true".equals(directUrl)) {
				String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");

				String nextJsp = preissuanceService.getNextJspPage(jspSequence, "/health-1", appSource);

				if (nextJsp.contains("psm")) {
					attemptedQstns = attemptedQstns - 3;
				} else if (nextJsp.contains("repsales")) {
					attemptedQstns = attemptedQstns - 1;
				}

				session.setAttribute("AttemptedQstns", attemptedQstns);
			}
			String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");
			if (jspSequence != null) {
				String nextJspPage = preissuanceService.getNextJspPage(jspSequence, "/health-1", appSource);
				redirectionPath = preissuancejsp + nextJspPage;
				if ("/otp".equalsIgnoreCase(nextJspPage)) {
					String agtChannelCode = session.getAttribute("AgtChannelCode") + "";
					if ("X".equalsIgnoreCase(agtChannelCode)) {
						redirectionPath = preissuancejsp + "verify";
					}
				}
				if (redirectionPath.contains("psm")) {
					Map<String, String> answerMap = preissuanceService.getCancerAnswerDetails("Psm", mpro_txn_id,
							createdBy);
					model.addAttribute("psmSurveyAnswerMap", answerMap);
				}
			} else {
				redirectionPath = preissuancejsp + "mproErrorPage";
			}

		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured : saveMproBuyerHealthAns6", ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("mPro Un Authorized Access Throwing customer to error page");
		logger.info("PreissuanceControllerMVC : saveMproBuyerHealthAns6() : END");
		// ADDED BY RK01318 STARTS
		if (isQuestionAndAnswerNull) {
			return "forward:/insurerhealth-3?isPrev=true&forward=true";
		}
		// ADDED BY RK01318 ENDS
		return redirectionPath;
	}

	@RequestMapping(value = "/cancer-1", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerCancer(Model model, HttpServletRequest request) {
		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}
		String redirectionPath = preissuancejsp + "/cancer-2";
		boolean isQuestionAndAnswerNull = false;
		String directUrl = request.getParameter("directUrl");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		String isPrev = request.getParameter("isPrev");
		logger.info("PreissuanceControllerMVC : saveMproBuyerCancer() : START");
		try {

			String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
			// @RK01318 Starts
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";

			if ("true".equals(axispolicyOrNot)) {
				mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.CANCER, mproTxnId);
			}

			// @RK01318 Ends

			ThreadContext.push("cancer1-section :" + mproTxnId);
			String category = "Cancer";
			Date currentDate = new java.util.Date();
			String createdBy = "mpro";
			List<MProBuyerDetailsDTO> questionList = new ArrayList<>();
			String forward = request.getParameter("forward");

			// Added By RK01318
			String platform = request.getParameter("platform");
			String userAgent = request.getParameter("userAgent");
			Map<String, String> mproCancerMap = preissuanceService.getCancerAnswerDetails(category, mproTxnId,
					createdBy);
			model.addAttribute("cancerSurveyAnswerMap", mproCancerMap);
			// Added By RK01318

			logger.info("starting to save cancer-1 screen for txnid : " + mproTxnId);

			MProBuyerDetailsDTO mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId,
					currentDate, category, "H13Ai", createdBy, request, "A_H13Ai", "Reflexive", "Boolean", "NA", false);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			questionList.add(mProBuyerDetailsDTO);
			if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H13Aii", createdBy, request, "A_H13Aii", "Secondary", "String", "H13Ai", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate, category,
					"H13Bi", createdBy, request, "A_H13Bi", "Reflexive", "Boolean", "NA", false);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			questionList.add(mProBuyerDetailsDTO);
			if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H13Bii", createdBy, request, "A_H13Bii", "Secondary", "String", "H13Bi", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate, category,
					"H13C", createdBy, request, "A_H13C", "Reflexive", "Boolean", "NA", false);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			questionList.add(mProBuyerDetailsDTO);
			if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H13Ci", createdBy, request, "A_H13Ci", "Secondary", "String", "H13C", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate, category,
					"H13D", createdBy, request, "A_H13D", "Reflexive", "Boolean", "NA", false);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			questionList.add(mProBuyerDetailsDTO);
			if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H13Di", createdBy, request, "A_H13Di", "Reflexive", "String", "H13D", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
				if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H13Dia", createdBy, request, "A_H13Dia", "Secondary", "String", "H13Di", false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);
				}

				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H13Dii", createdBy, request, "A_H13Dii", "Reflexive", "String", "H13D", true);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
				if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H13Diib", createdBy, request, "A_H13Diib", "Secondary", "String", "H13Dii",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);
				}

				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H13Diii", createdBy, request, "A_H13Diii", "Reflexive", "String", "H13D", true);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
				if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H13Diiia", createdBy, request, "A_H13Diiia", "Secondary", "String", "H13Diii",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);
				}

				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H13Div", createdBy, request, "A_H13Div", "Reflexive", "String", "H13D", true);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
				if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H13Diva", createdBy, request, "A_H13Diva", "Secondary", "String", "H13Div",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);
				}
			}

			logger.info("saveMproBuyerCancer() saveMproBuyerDetails : START");
			if ((!isQuestionAndAnswerNull || "true".equals(isPrev)) && !("true".equals(forward))) {
				// ADDED BY RK01318 7-March-2019 STARTS
				APIErrorDTO errordto = new APIErrorDTO();
				model.addAttribute("error", errordto);
				// ADDED BY RK01318 7-March-2019 ENDS
			}

			if (!"true".equals(isPrev) && !isQuestionAndAnswerNull) {
				if ("true".equals(axispolicyOrNot)) {
					mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
					preissuanceService.saveMproBuyerDetails(questionList, mproTxnId, Constants.CANCER);
				} else {
					preissuanceService.saveMproBuyerDetails(questionList, null, Constants.CANCER);
				}

				attemptedQstns = attemptedQstns + 4;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			} else if (!"true".equals(directUrl)) {
				attemptedQstns = attemptedQstns - 2;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			}
			logger.info("saveMproBuyerCancer() saveMproBuyerDetails : END");

		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured :saveMproBuyerCancer " + ex);
			logger.info("mPro Un Authorized Access Throwing customer to error page");
			redirectionPath = preissuancejsp + "/mproErrorPage";

		} finally {
			ThreadContext.pop();
		}
		logger.info("PreissuanceControllerMVC : saveMproBuyerCancer() : END");
		// ADDED BY RK01318 STARTS
		List listType = (List) session.getAttribute("listType");
		String mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
		if (isQuestionAndAnswerNull) {

			if (listType.contains("PRODUCT")) {
				return "forward:/product-1?isPrevtemp=true&forward=true";
			} else {
				return "forward:/mprobuyer?txnId=" + mpro_txn_id;
			}

		}
		// ADDED BY RK01318 ENDS
		return redirectionPath;
	}

	@RequestMapping(value = "/cancer-2", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerCancer2(Model model, HttpServletRequest request) {
		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}
		String redirectionPath = preissuancejsp + "/cancer-2";
		logger.info("PreissuanceControllerMVC : saveMproBuyerCancer() : START");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		boolean isQuestionAndAnswerNull = false;
		String directUrl = request.getParameter("directUrl");
		String appSource = session.getAttribute("appSource") + "";

		try {

			String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
			// @RK01318 Starts
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";
			if ("true".equals(axispolicyOrNot)) {
				mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.CANCER, mproTxnId);
			}

			// @RK01318 Ends
			String isPrev = request.getParameter("isPrev");
			ThreadContext.push("cancer2-section :" + mproTxnId);
			String category = "Cancer";
			Date currentDate = new java.util.Date();
			String createdBy = "mpro";
			List<MProBuyerDetailsDTO> questionList = new ArrayList<>();
			String forward = request.getParameter("forward");

			// Added By RK01318 Starts
			String platform = request.getParameter("platform");
			String userAgent = request.getParameter("userAgent");

			String isWopInsured = session.getAttribute("isWopInsured") + "";

			if ("true".equals(isWopInsured)) {
				Map<String, String> mproCancerMap = preissuanceService.getCancerAnswerDetails(category, mproTxnId,
						createdBy);
				model.addAttribute("cancerSurveyAnswerMap", mproCancerMap);
			}

			Map<String, String> answerMap = preissuanceService.getCancerAnswerDetails("Psm", mproTxnId, createdBy);
			model.addAttribute("psmSurveyAnswerMap", answerMap);
			// Added By RK01318 Ends
			logger.info("starting to save cancer-2 screen for txnid : " + mproTxnId);

			MProBuyerDetailsDTO mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId,
					currentDate, category, "H13E", createdBy, request, "A_H13E", "Reflexive", "Boolean", "NA", false);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);
			if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H13Ei", createdBy, request, "A_H13Ei", "Secondary", "String", "H13E", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate, category,
					"H13F", createdBy, request, "A_H13F", "Reflexive", "Boolean", "NA", false);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);
			if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H13fi", createdBy, request, "A_H13fi", "Reflexive", "String", "H13F", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
				if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H13Fia", createdBy, request, "A_H13Fia", "Secondary", "String", "H13fi", false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H13Fib", createdBy, request, "A_H13Fib", "Secondary", "String", "H13fi", false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H13Fic", createdBy, request, "A_H13Fic", "Secondary", "String", "H13fi", false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H13Fid", createdBy, request, "A_H13Fid", "Secondary", "String", "H13fi", false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

				}

				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H13Fii", createdBy, request, "A_H13Fii", "Reflexive", "String", "H13F", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
				if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H13Fiia", createdBy, request, "A_H13Fiia", "Secondary", "String", "H13Fii",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H13Fiib", createdBy, request, "A_H13Fiib", "Secondary", "String", "H13Fii",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H13Fiic", createdBy, request, "A_H13Fiic", "Secondary", "String", "H13Fii",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H13Fiid", createdBy, request, "A_H13Fiid", "Secondary", "String", "H13Fii",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);
				}
			}

			logger.info("saveMproBuyerCancer2() saveMproBuyerDetails : START");
			if ((!isQuestionAndAnswerNull || "true".equals(isPrev)) && !("true".equals(forward))) {
				// ADDED BY RK01318 7-March-2019 STARTS
				APIErrorDTO errordto = new APIErrorDTO();
				model.addAttribute("error", errordto);
				// ADDED BY RK01318 7-March-2019 ENDS
			}

			if (!"true".equals(isPrev) && !isQuestionAndAnswerNull) {
				if ("true".equals(axispolicyOrNot)) {
					mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
					preissuanceService.saveMproBuyerDetails(questionList, mproTxnId, Constants.CANCER);
				} else {
					preissuanceService.saveMproBuyerDetails(questionList, null, Constants.CANCER);
				}

				attemptedQstns = attemptedQstns + 2;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			} else if (!"true".equals(directUrl)) {

				String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");
				String nextJsp = preissuanceService.getNextJspPage(jspSequence, "/cancer-1", appSource);
				// @RK01318 Starts

				if ("true".equals(isWopInsured)) {
					attemptedQstns = attemptedQstns - 4;
				} else {
					// @RK01318 Ends
					if (nextJsp.contains("psm")) {
						attemptedQstns = attemptedQstns - 3;
					} else if (nextJsp.contains("repsales")) {
						attemptedQstns = attemptedQstns - 1;
					}
				}
				session.setAttribute("AttemptedQstns", attemptedQstns);
			}
			logger.info("saveMproBuyerCancer2() saveMproBuyerDetails : END");

			String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");

			if (jspSequence != null) {

				// @RK01318 STARTS

				if ("true".equals(isWopInsured)) {
					return preissuancejsp + "insurercancer-1";
				}
				redirectionPath = preissuancejsp
						+ preissuanceService.getNextJspPage(jspSequence, "/cancer-1", appSource);
				// Added By RK01318 so that previous of health-1 will work
				// starts
				mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
				if ("true".equals(axispolicyOrNot)) {
					mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH, mproTxnId);
				}
				Map<String, String> healthAnswerSurveyMap = preissuanceService.getCancerAnswerDetails("Health",
						mproTxnId, "mpro");
				model.addAttribute("healthAnswerSurveyMap", healthAnswerSurveyMap);
				// Added By RK01318 so that previous of health-1 will work ends

			} else {
				redirectionPath = preissuancejsp + "mproErrorPage";
			}

		} catch (Exception e) {
			logger.error("mPro Something went wrong : Exception occured :saveMproBuyerCancer2 " + e);
			logger.info("mPro Un Authorized Access Throwing customer to error page");
			redirectionPath = preissuancejsp + "/mproErrorPage";
		} finally {
			ThreadContext.pop();
		}
		if (isQuestionAndAnswerNull) {
			return "forward:/cancer-1?isPrev=true&forward=true";
		}
		try {
			String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");
			String nextJsp = preissuanceService.getNextJspPage(jspSequence, "/cancer-2", appSource);
			if ("/otp".equalsIgnoreCase(nextJsp)) {
				String agtChannelCode = session.getAttribute("AgtChannelCode") + "";
				if ("X".equalsIgnoreCase(agtChannelCode)) {
					redirectionPath = preissuancejsp + "verify";
				}
			}
		}

		catch (Exception e) {
			logger.error("While checking axis policy Exception occured :: /health-6" + e);
		}
		return redirectionPath;
	}

	@RequestMapping(value = "/insurercancer-1", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerInsurerCancer(Model model, HttpServletRequest request) {
		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}
		String redirectionPath = preissuancejsp + "/insurercancer-2";
		boolean isQuestionAndAnswerNull = false;
		String directUrl = request.getParameter("directUrl");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		String isPrev = request.getParameter("isPrev");
		logger.info("PreissuanceControllerMVC : saveMproBuyerCancer() : START");
		try {

			String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
			// @RK01318 Starts
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";

			if ("true".equals(axispolicyOrNot)) {
				mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.CANCER, mproTxnId);
			}

			// @RK01318 Ends

			ThreadContext.push("cancer1-section :" + mproTxnId);
			String category = "Cancer";
			Date currentDate = new java.util.Date();
			String createdBy = "mpro";
			List<MProBuyerDetailsDTO> questionList = new ArrayList<>();
			String forward = request.getParameter("forward");

			// Added By RK01318
			String platform = request.getParameter("platform");
			String userAgent = request.getParameter("userAgent");
			Map<String, String> mproCancerMap = preissuanceService.getCancerAnswerDetails(category, mproTxnId,
					createdBy);
			model.addAttribute("cancerSurveyAnswerMap", mproCancerMap);
			// Added By RK01318

			logger.info("starting to save cancer-1 screen for txnid : " + mproTxnId);

			MProBuyerDetailsDTO mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId,
					currentDate, category, "H37Ai", createdBy, request, "A_H37Ai", "Reflexive", "Boolean", "NA", false);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			questionList.add(mProBuyerDetailsDTO);
			if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H37Aii", createdBy, request, "A_H37Aii", "Secondary", "String", "H37Ai", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate, category,
					"H37Bi", createdBy, request, "A_H37Bi", "Reflexive", "Boolean", "NA", false);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			questionList.add(mProBuyerDetailsDTO);
			if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H37Bii", createdBy, request, "A_H37Bii", "Secondary", "String", "H37Bi", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate, category,
					"H37C", createdBy, request, "A_H37C", "Reflexive", "Boolean", "NA", false);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			questionList.add(mProBuyerDetailsDTO);
			if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H37Ci", createdBy, request, "A_H37Ci", "Secondary", "String", "H37C", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate, category,
					"H37D", createdBy, request, "A_H37D", "Reflexive", "Boolean", "NA", false);
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			questionList.add(mProBuyerDetailsDTO);
			if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H37Di", createdBy, request, "A_H37Di", "Reflexive", "String", "H37D", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
				if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H37Dia", createdBy, request, "A_H37Dia", "Secondary", "String", "H37Di", false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);
				}

				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H37Dii", createdBy, request, "A_H37Dii", "Reflexive", "String", "H37D", true);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
				if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H37Diib", createdBy, request, "A_H37Diib", "Secondary", "String", "H37Dii",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);
				}

				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H37Diii", createdBy, request, "A_H37Diii", "Reflexive", "String", "H37D", true);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
				if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H37Diiia", createdBy, request, "A_H37Diiia", "Secondary", "String", "H37Diii",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);
				}

				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H37Div", createdBy, request, "A_H37Div", "Reflexive", "String", "H37D", true);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
				if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H37Diva", createdBy, request, "A_H37Diva", "Secondary", "String", "H37Div",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);
				}
			}

			logger.info("saveMproBuyerCancer() saveMproBuyerDetails : START");
			if ((!isQuestionAndAnswerNull || "true".equals(isPrev)) && !("true".equals(forward))) {
				// ADDED BY RK01318 7-March-2019 STARTS
				APIErrorDTO errordto = new APIErrorDTO();
				model.addAttribute("error", errordto);
				// ADDED BY RK01318 7-March-2019 ENDS
			}

			if (!"true".equals(isPrev) && !isQuestionAndAnswerNull) {
				if ("true".equals(axispolicyOrNot)) {
					mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
					preissuanceService.saveMproBuyerDetails(questionList, mproTxnId, Constants.CANCER);
				} else {
					preissuanceService.saveMproBuyerDetails(questionList, null, Constants.CANCER);
				}

				attemptedQstns = attemptedQstns + 4;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			} else if (!"true".equals(directUrl)) {
				attemptedQstns = attemptedQstns - 2;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			}
			logger.info("saveMproBuyerCancer() saveMproBuyerDetails : END");

		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured :saveMproBuyerCancer " + ex);
			logger.info("mPro Un Authorized Access Throwing customer to error page");
			redirectionPath = preissuancejsp + "/mproErrorPage";

		} finally {
			ThreadContext.pop();
		}
		logger.info("PreissuanceControllerMVC : saveMproBuyerCancer() : END");
		// ADDED BY RK01318 STARTS
		if (isQuestionAndAnswerNull) {
			return "forward:/cancer-2?isPrev=true&forward=true";
		}
		// ADDED BY RK01318 ENDS
		return redirectionPath;
	}

	@RequestMapping(value = "/insurercancer-2", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerInsurerCancer2(Model model, HttpServletRequest request) {
		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}
		String redirectionPath = preissuancejsp + "/insurercancer-2";
		logger.info("PreissuanceControllerMVC : saveMproBuyerCancer() : START");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		boolean isQuestionAndAnswerNull = false;
		String directUrl = request.getParameter("directUrl");
		String appSource = session.getAttribute("appSource") + "";
		try {

			String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
			// @RK01318 Starts
			String axispolicyOrNot = request.getSession().getAttribute("axispolicyOrNot") + "";
			if ("true".equals(axispolicyOrNot)) {
				mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.CANCER, mproTxnId);
			}

			// @RK01318 Ends
			String isPrev = request.getParameter("isPrev");
			ThreadContext.push("cancer2-section :" + mproTxnId);
			String category = "Cancer";
			Date currentDate = new java.util.Date();
			String createdBy = "mpro";
			List<MProBuyerDetailsDTO> questionList = new ArrayList<>();
			String forward = request.getParameter("forward");

			// Added By RK01318 Starts
			String platform = request.getParameter("platform");
			String userAgent = request.getParameter("userAgent");
			Map<String, String> answerMap = preissuanceService.getCancerAnswerDetails("Psm", mproTxnId, createdBy);
			model.addAttribute("psmSurveyAnswerMap", answerMap);
			// Added By RK01318 Ends
			logger.info("starting to save cancer-2 screen for txnid : " + mproTxnId);

			MProBuyerDetailsDTO mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId,
					currentDate, category, "H37E", createdBy, request, "A_H37E", "Reflexive", "Boolean", "NA", false);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);
			if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H37Ei", createdBy, request, "A_H37Ei", "Secondary", "String", "H37E", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
			}

			mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate, category,
					"H37F", createdBy, request, "A_H37F", "Reflexive", "Boolean", "NA", false);
			// ADDED BY RK01318 STARTS
			if (!("true".equals(isPrev)) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
					|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
				isQuestionAndAnswerNull = true;
				// ADDED BY RK01318 7-March-2019 STARTS
				addErrorDataInModel(model, mProBuyerDetailsDTO, request);
				// ADDED BY RK01318 7-March-2019 ENDS
			}
			// ADDED BY RK01318 ENDS
			mProBuyerDetailsDTO.setUserAgent(userAgent);
			mProBuyerDetailsDTO.setPlatform(platform);
			questionList.add(mProBuyerDetailsDTO);
			if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H37fi", createdBy, request, "A_H37fi", "Reflexive", "String", "H37F", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
				if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H37Fia", createdBy, request, "A_H37Fia", "Secondary", "String", "H37fi", false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H37Fib", createdBy, request, "A_H37Fib", "Secondary", "String", "H37fi", false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H37Fic", createdBy, request, "A_H37Fic", "Secondary", "String", "H37fi", false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H37Fid", createdBy, request, "A_H37Fid", "Secondary", "String", "H37fi", false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

				}

				mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
						category, "H37Fii", createdBy, request, "A_H37Fii", "Reflexive", "String", "H37F", false);
				mProBuyerDetailsDTO.setUserAgent(userAgent);
				mProBuyerDetailsDTO.setPlatform(platform);
				questionList.add(mProBuyerDetailsDTO);
				if (mProBuyerDetailsDTO != null && "Y".equalsIgnoreCase(mProBuyerDetailsDTO.getANSWER())) {
					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H37Fiia", createdBy, request, "A_H37Fiia", "Secondary", "String", "H37Fii",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H37Fiib", createdBy, request, "A_H37Fiib", "Secondary", "String", "H37Fii",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H37Fiic", createdBy, request, "A_H37Fiic", "Secondary", "String", "H37Fii",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);

					mProBuyerDetailsDTO = preissuanceService.getMproBuyerDetailsDtoFromRequest(mproTxnId, currentDate,
							category, "H37Fiid", createdBy, request, "A_H37Fiid", "Secondary", "String", "H37Fii",
							false);
					mProBuyerDetailsDTO.setUserAgent(userAgent);
					mProBuyerDetailsDTO.setPlatform(platform);
					questionList.add(mProBuyerDetailsDTO);
				}
			}

			logger.info("saveMproBuyerCancer2() saveMproBuyerDetails : START");
			if ((!isQuestionAndAnswerNull || "true".equals(isPrev)) && !("true".equals(forward))) {
				// ADDED BY RK01318 7-March-2019 STARTS
				APIErrorDTO errordto = new APIErrorDTO();
				model.addAttribute("error", errordto);
				// ADDED BY RK01318 7-March-2019 ENDS
			}

			if (!"true".equals(isPrev) && !isQuestionAndAnswerNull) {
				if ("true".equals(axispolicyOrNot)) {
					mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
					preissuanceService.saveMproBuyerDetails(questionList, mproTxnId, Constants.CANCER);
				} else {
					preissuanceService.saveMproBuyerDetails(questionList, null, Constants.CANCER);
				}

				attemptedQstns = attemptedQstns + 2;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			} else if (!"true".equals(directUrl)) {

				String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");
				String nextJsp = preissuanceService.getNextJspPage(jspSequence, "/cancer-1", appSource);
				if (nextJsp.contains("psm")) {
					attemptedQstns = attemptedQstns - 3;
				} else if (nextJsp.contains("repsales")) {
					attemptedQstns = attemptedQstns - 1;
				}

				session.setAttribute("AttemptedQstns", attemptedQstns);
			}
			logger.info("saveMproBuyerCancer2() saveMproBuyerDetails : END");

			String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");

			if (jspSequence != null) {

				// @RK01318 STARTS

				redirectionPath = preissuancejsp
						+ preissuanceService.getNextJspPage(jspSequence, "/cancer-1", appSource);

				// Added By RK01318 so that previous of health-1 will work
				// starts
				mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
				if ("true".equals(axispolicyOrNot)) {
					mproTxnId = preissuanceService.getTransactionOfHealthAndCancer(Constants.HEALTH, mproTxnId);
				}
				Map<String, String> healthAnswerSurveyMap = preissuanceService.getCancerAnswerDetails("Health",
						mproTxnId, "mpro");
				model.addAttribute("healthAnswerSurveyMap", healthAnswerSurveyMap);
				// Added By RK01318 so that previous of health-1 will work ends

			} else {
				redirectionPath = preissuancejsp + "mproErrorPage";
			}

		} catch (Exception e) {
			logger.error("mPro Something went wrong : Exception occured :saveMproBuyerCancer2 " + e);
			logger.info("mPro Un Authorized Access Throwing customer to error page");
			redirectionPath = preissuancejsp + "/mproErrorPage";
		} finally {
			ThreadContext.pop();
		}
		if (isQuestionAndAnswerNull) {
			return "forward:/insurercancer-1?isPrev=true&forward=true";
		}
		try {
			String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");
			String nextJsp = preissuanceService.getNextJspPage(jspSequence, "/cancer-2", appSource);
			if ("/otp".equalsIgnoreCase(nextJsp)) {
				String agtChannelCode = session.getAttribute("AgtChannelCode") + "";
				if ("X".equalsIgnoreCase(agtChannelCode)) {
					redirectionPath = preissuancejsp + "verify";
				}
			}
		}

		catch (Exception e) {
			logger.error("While checking axis policy Exception occured :: /health-6" + e);
		}
		return redirectionPath;
	}

	@RequestMapping(value = "/psm-1", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerAnsPsm(Model model, HttpServletRequest request) {
		String redirectionPath = preissuancejsp;
		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}
		logger.info("PreissuanceControllerMVC : saveMproBuyerAnsPsm() : START");
		int attemptedQstns = Integer.parseInt(
				session.getAttribute("AttemptedQstns") != null ? session.getAttribute("AttemptedQstns") + "" : "0");
		boolean isQuestionAndAnswerNull = false;
		String appSource = session.getAttribute("appSource") + "";
		String isPrev = request.getParameter("isPrev");
		String directUrl = request.getParameter("directUrl");
		try {

			String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
			ThreadContext.push("psm-section :" + mproTxnId);
			String axispolicyOrNot = session.getAttribute("axispolicyOrNot") + "";
			String transactions[] = (String[]) session.getAttribute("axistransaction");
			String psm1 = request.getParameter("PSM1");
			String ansPsm1 = request.getParameter("A_PSM1");
			String psm2 = request.getParameter("PSM2");
			String ansPsm2 = request.getParameter("A_PSM2");
			String psm3 = request.getParameter("PSM3");
			String ansPsm3 = request.getParameter("A_PSM3");

			if ("true".equals(axispolicyOrNot)) {
				for (int i = 0; i < transactions.length; i++) {
					if (preissuanceDao.isTransactionApplicableForType(transactions[i], ProductType.PSM)) {
						insertPSMQuestions(transactions[i], isPrev, isQuestionAndAnswerNull, model);
					}
				}
			} else {
				insertPSMQuestions(mproTxnId, isPrev, isQuestionAndAnswerNull, model);
			}
			if (!"true".equals(isPrev)
					&& (StringUtility.checkFieldIsNull(psm1) || StringUtility.checkFieldIsNull(ansPsm1)
							|| StringUtility.checkFieldIsNull(psm2) || StringUtility.checkFieldIsNull(ansPsm2)
							|| StringUtility.checkFieldIsNull(psm3) || StringUtility.checkFieldIsNull(ansPsm3))) {
				isQuestionAndAnswerNull = true;
			}

			String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");

			if ("true".equals(isPrev) && !"true".equals(directUrl)) {
				attemptedQstns = attemptedQstns - 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			} else if (!"true".equals(directUrl)) {
				attemptedQstns = attemptedQstns + 3;
				session.setAttribute("AttemptedQstns", attemptedQstns);
			}

			if (jspSequence != null) {

				try {
					jspSequence = (String[]) request.getSession().getAttribute("validjsplist");
					String nextJsp = preissuanceService.getNextJspPage(jspSequence, "/psm-1", appSource);
					if ("/otp".equalsIgnoreCase(nextJsp)) {
						String agtChannelCode = session.getAttribute("AgtChannelCode") + "";
						if ("X".equalsIgnoreCase(agtChannelCode)) {
							redirectionPath = preissuancejsp + "verify";
						} else {
							redirectionPath = preissuancejsp
									+ preissuanceService.getNextJspPage(jspSequence, "/psm-1", appSource);
						}
					} else {
						redirectionPath = preissuancejsp + nextJsp;
					}
				} catch (Exception e) {
					logger.error("While checking axis policy Exception occured :: /health-6" + e);
				}
			} else {
				redirectionPath = preissuancejsp + "mproErrorPage";
			}

		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured :saveMproBuyerAnsPsm " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("PreissuanceControllerMVC : saveMproBuyerAnsPsm() : END");
		String mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
		// RK01318 STARTS
		boolean isWopInsured = preissuanceService.isInsurerApplicable(mpro_txn_id);

		if (isQuestionAndAnswerNull) {
			List<String> listType = (List) session.getAttribute("listType");
			if (listType != null && !listType.isEmpty() && listType.contains("CANCER")) {

				if (isWopInsured) {
					return "forward:/insurercancer-2?isPrev=true&forward=true";
				} else {
					return "forward:/cancer-2?isPrev=true&forward=true";
				}

			} else if (listType != null && !listType.isEmpty()
					&& (listType.contains("HEALTH") || listType.contains("CANCER,HEALTH"))) {
				if (isWopInsured) {
					return "forward:/insurerhealth-4?isPrev=true&forward=true";
				} else {
					return "forward:/health-4?isPrev=true&forward=true";
				}
			} else if (listType != null && !listType.isEmpty() && listType.contains("PRODUCT")) {
				return "forward:/product-1?isPrevtemp=true&forward=true";
			} else {
				return "forward:/mprobuyer?txnId=" + mpro_txn_id;
			}
		}
		// RK01318 ENDS
		return redirectionPath;
	}

	@RequestMapping(value = "/repsales-1", method = { RequestMethod.GET, RequestMethod.POST })
	public String saveMproBuyerAnsRepSales(Model model, HttpServletRequest request) {
		if (errorInGetMethod(request)) {

			return preissuancejsp + "mproErrorPage";
		}
		String redirectionPath = preissuancejsp + "otp";
		logger.info("PreissuanceControllerMVC : saveMproBuyerAnsRepSales() : START");
		Boolean isQuestionAndAnswerNull = false;
		String isPrev = request.getParameter("isPrev");
		String forward = request.getParameter("forward");
		try {

			String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
			ThreadContext.push("replacement sales -section :" + mproTxnId);
			String appSource = session.getAttribute("appSource") + "";

			String axispolicyOrNot = session.getAttribute("axispolicyOrNot") + "";

			logger.info("Mpro saveMproBuyerDetails for replacement sales: START");
			if (("true".equals(isPrev)) && !("true".equals(forward))) {
				// ADDED BY RK01318 7-March-2019 STARTS
				APIErrorDTO errordto = new APIErrorDTO();
				model.addAttribute("error", errordto);
			}
			// ADDED BY RK01318 7-March-2019 ENDS
			String rs1 = request.getParameter("RS1");
			String ansRs1 = request.getParameter("A_RS1");
			if (!"true".equals(isPrev) && StringUtility.checkFieldIsNull(rs1)
					|| StringUtility.checkFieldIsNull(ansRs1)) {
				isQuestionAndAnswerNull = true;
			}

			// @RK01318 Starts
			if ("true".equals(axispolicyOrNot)) {
				String[] transaction = (String[]) session.getAttribute("axistransaction");

				for (int i = 0; i < transaction.length; i++) {
					if (preissuanceDao.isTransactionApplicableForType(transaction[i], ProductType.REPSALES)) {
						List<MProBuyerDetailsDTO> questionList = repsalesInsertion(transaction[i], isPrev, model,
								redirectionPath);

						if (!"true".equals(isPrev)) {
							if (!preissuanceService.saveMproBuyerDetails(questionList, null, Constants.RPSALES)) {
								logger.debug("Failed to save the data.");
								redirectionPath = preissuancejsp + "mproErrorPage";
								break;
							}
						}
					}
				}
			} else {
				List<MProBuyerDetailsDTO> questionList = repsalesInsertion(mproTxnId, isPrev, model, redirectionPath);
				if (!"true".equals(isPrev)) {
					if (!preissuanceService.saveMproBuyerDetails(questionList, null, Constants.RPSALES)) {
						logger.debug("Failed to save the data.");
						redirectionPath = preissuancejsp + "mproErrorPage";
					}
				}
			}
			// @RK01318 Ends

			logger.info("Mpro saveMproBuyerDetails for replacement sales: END");
			String[] jspSequence = (String[]) request.getSession().getAttribute("validjsplist");
			if (jspSequence != null) {

				try {
					jspSequence = (String[]) request.getSession().getAttribute("validjsplist");
					String nextJsp = preissuanceService.getNextJspPage(jspSequence, "/repsales-1", appSource);
					if ("/otp".equalsIgnoreCase(nextJsp)) {
						String agtChannelCode = session.getAttribute("AgtChannelCode") + "";
						if ("X".equalsIgnoreCase(agtChannelCode)) {
							redirectionPath = preissuancejsp + "verify";
						} else {
							redirectionPath = preissuancejsp
									+ preissuanceService.getNextJspPage(jspSequence, "/repsales-1", appSource);
						}
					} else {
						redirectionPath = preissuancejsp + nextJsp;
					}
				} catch (Exception e) {
					logger.error("While checking axis policy Exception occured :: /health-6" + e);
				}

			} else {
				redirectionPath = preissuancejsp + "mproErrorPage";
			}
		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured :saveMproBuyerAnsRepSales " + ex);
		} finally {
			ThreadContext.pop();
		}

		logger.info("PreissuanceControllerMVC : saveMproBuyerAnsRepSales() : END");
		String mpro_txn_id = (String) request.getSession().getAttribute("mpro_txn_id");
		boolean isWopInsured = preissuanceService.isInsurerApplicable(mpro_txn_id);
		if (isQuestionAndAnswerNull) {
			List<String> listType = (List) session.getAttribute("listType");
			if (listType != null && !listType.isEmpty() && listType.contains("PSM")) {
				return "forward:/psm-1?isPrev=true&forward=true";
			} else {
				if (listType != null && !listType.isEmpty() && listType.contains("CANCER")) {
					if (isWopInsured) {
						return "forward:/insurercancer-2?isPrev=true&forward=true";
					} else {
						return "forward:/cancer-2?isPrev=true&forward=true";
					}

				} else if (listType != null && !listType.isEmpty()
						&& (listType.contains("HEALTH") || listType.contains("CANCER,HEALTH"))) {
					if (isWopInsured) {
						return "forward:/insurerhealth-4?isPrev=true&forward=true";
					} else {
						return "forward:/health-4?isPrev=true&forward=true";
					}

				} else if (listType != null && !listType.isEmpty() && listType.contains("PRODUCT")) {
					return "forward:/product-1?isPrevtemp=true&forward=true";
				} else {
					return "forward:/mprobuyer?txnId=" + mpro_txn_id;
				}
			}
		}

		return redirectionPath;
	}

	@RequestMapping(value = "/verifyotp", method = { RequestMethod.GET, RequestMethod.POST })
	public String verifyOtp(Model model, HttpServletRequest request, HttpSession session) {
		logger.info("PreissuanceControlMVC : verifyOtp() : START");

		String redirection = preissuancejsp + "mprosuccess";
		String statusMessage = "Error! please try again later.";
		logger.info("PreissuanceControllerMVC : verifyOtp() : START");
		try {

			String appSource = session.getAttribute("appSource") + "";

			String otp1 = request.getParameter("OTP1");
			String otp2 = request.getParameter("OTP2");
			String otp3 = request.getParameter("OTP3");
			String otp4 = request.getParameter("OTP4");

			// @RK01318 STARTS AXIS POLICY OR NOT STARTS
			String[] axistransaction = (String[]) session.getAttribute("axistransaction");
			String axispolicyOrNot = session.getAttribute("axispolicyOrNot") + "";
			// @RK01318 ENDS AXIS POLICY OR NOT ENDS

			String otpStr = otp1 + otp2 + otp3 + otp4;
			final String txnid = (String) request.getSession().getAttribute("mpro_txn_id");
			try {
				ThreadContext.push("verify otp -section :" + txnid);
				logger.info("Otp fetched successfully.");
				Integer otp = Integer.parseInt(otpStr);
				logger.info("Otp parsed successfully.");
				if (txnid != null) {
					logger.info("validating otp : START");
					if ("true".equals(axispolicyOrNot)) {
						for (int i = 0; i < axistransaction.length; i++) {
							statusMessage = preissuanceService.validateOtp(axistransaction[i], otp);
						}
					} else {
						statusMessage = preissuanceService.validateOtp(txnid, otp);
					}
					logger.info("validating otp : END with statusmessage :" + statusMessage);
				}

			} catch (Exception e) {
				logger.debug("Not a valid OTP entered" + e);
				statusMessage = "";
			}

			session.setAttribute("otp_status_message", statusMessage);

			if (statusMessage.equalsIgnoreCase(Constants.SUCCESS)) {

				if ("true".equals(axispolicyOrNot)) {
					for (int i = 0; i < 2; i++) {
						backflowProcess(axistransaction[i], appSource);// added
																		// app
																		// source
																		// for
																		// multi
																		// environment
						sendSMSandMailAfterOTPConfirmation(txnid, axistransaction[i]);
					}

				} else {
					backflowProcess(txnid, appSource);// added app source for
														// multi environment
					sendSMSandMailAfterOTPConfirmation(null, txnid);
				}
				session.removeAttribute("mpro_txn_id");
				session.removeAttribute("seller_mpro_dto");
				session.removeAttribute("question_pos");
				session.removeAttribute("validjsplist");
				session.removeAttribute("productQuestionDto");
				session.removeAttribute(Constants.SLIDEQUESTIONDTOS);
				session.removeAttribute("otp_status_message");
				logger.info("removed all session attributes.");
			}

		} catch (Exception ex) {
			logger.error("mPro Something went wrong : Exception occured : " + ex);
		} finally {
			ThreadContext.pop();
			if (!statusMessage.equalsIgnoreCase(Constants.SUCCESS)) {
				redirection = preissuancejsp + "/otp";
			} else {
				redirection = preissuancejsp + "mprosuccess";
			}
		}
		logger.info("PreissuanceControllerMVC : verifyOtp() : END");
		return redirection;
	}

	@RequestMapping(value = "customer-sec", method = { RequestMethod.GET, RequestMethod.POST })
	public String customerSection(Model model) {
		try {
			String txnId = (String) session.getAttribute(Constants.TXNID);
			String appSource = (String) session.getAttribute("appSource");
			if (txnId != null) {
				ThreadContext.push("customer-sec : " + txnId);
				logger.info("Going to fetch All questions for customer : start");
				Boolean emailCheck = preissuanceService.checkEmailId(txnId, appSource);
				List<SlideQuestionDto> slideQuestionDtos = preissuanceService.getSlideQuestionsByTxnId(txnId,
						emailCheck, appSource);
				logger.info("Going to fetch All questions for customer : end");
				SlideQuestionDto retDto = new SlideQuestionDto();
				for (SlideQuestionDto dto : slideQuestionDtos) {
					logger.info("Questions are : " + slideQuestionDtos.toString());
					logger.info("QuestionData is : " + dto.toString());
					retDto = dto;
					break;
				}
				model.addAttribute("percent", 0);
				model.addAttribute("questionDto", retDto);
				session.setAttribute(Constants.SLIDEQUESTIONDTOS, slideQuestionDtos);
				logger.info("Returning to View");
				return preissuancejsp + "customer-sec";
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured inside customerSection :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("Un Authorized Access Throwing customer to error page");
		return preissuancejsp + Constants.ERRORPAGE;
	}

	@RequestMapping(value = "/sendAnswer", method = { RequestMethod.GET, RequestMethod.POST })
	public String addUser(@ModelAttribute(value = "slideQuestionDto") SlideQuestionDto slideQuestionDto, Model model) {
		try {
			String txnId = (String) session.getAttribute("txnId");

			List<String> buyerActivityList = (List<String>) session.getAttribute("buyerActivityList");
			if (buyerActivityList == null || buyerActivityList.isEmpty()) {
				buyerActivityList = new ArrayList<>();
			}
			SlideQuestionDto retDto = new SlideQuestionDto();
			if (txnId != null) {
				ThreadContext.push("sendAnswer : " + txnId);
				logger.info("saveUpdateBuyerRecords : Start : " + slideQuestionDto.toString());
				buyerActivityList.add(slideQuestionDto.getQuestionName());
				buyerActivityList.add(slideQuestionDto.getComment());
				buyerActivityList.add(formatter.format(new Date()));
				if (!slideQuestionDto.getCount().contains("-1")) {
					APIErrorDTO errorDTO = new APIErrorDTO();
					errorDTO.setErrorCode(500);
					if (slideQuestionDto.getErrorcount().trim() != null
							&& "".equals(slideQuestionDto.getErrorcount().trim())) {
						errorDTO.setErrorcount(1);
					} else {
						errorDTO.setErrorcount(Integer.parseInt(slideQuestionDto.getErrorcount().trim()) + 1);
					}
					errorDTO.setErrorDescription("either Question or Answer Code is null");
					if (null == slideQuestionDto.getQuestionName() || "null".equals(slideQuestionDto.getQuestionName())
							|| "".equals(slideQuestionDto.getQuestionName()) || null == slideQuestionDto.getComment()
							|| "null".equals(slideQuestionDto.getComment())
							|| "".equals(slideQuestionDto.getComment())) {
						model.addAttribute("error", errorDTO);
					} else {
						boolean updateStatus = preissuanceService.saveUpdateBuyerRecords(slideQuestionDto, txnId);
						logger.info("saveUpdateBuyerRecords : End : " + updateStatus);
					}
				}
				session.setAttribute("buyerActivityList", buyerActivityList);
				@SuppressWarnings("unchecked")
				List<SlideQuestionDto> slideQuestionDtos = (List<SlideQuestionDto>) session
						.getAttribute("slideQuestionDtos");

				int quesNo;
				String check = "";
				String comment;
				if (!model.containsAttribute("error")) {
					if (slideQuestionDto.getCount().contains("-1")) {
						quesNo = Integer.parseInt(slideQuestionDto.getQuestionSeq()) - 1;
					} else {
						quesNo = Integer.parseInt(slideQuestionDto.getQuestionSeq()) + 1;
						for (SlideQuestionDto dto : slideQuestionDtos) {
							if (Integer.valueOf(dto.getQuestionSeq()) == (quesNo)) {
								check = "check";
							}
						}
						if (!check.equalsIgnoreCase("check")) {
							quesNo = quesNo + 1;
						}
					}
				} else {
					quesNo = Integer.parseInt(slideQuestionDto.getQuestionSeq());
				}
				SlideQuestionDto ddTo = new SlideQuestionDto();
				ddTo.setQuestionSeq(Integer.toString(quesNo));
				SlideQuestionDto ansDto = preissuanceService.getQuestionAns(ddTo, txnId);
				if (ansDto != null) {
					comment = ansDto.getComment();
				} else {
					comment = "NOT_FOUND";
				}

				int percentage = 0;
				if (slideQuestionDtos != null && !slideQuestionDtos.isEmpty()) {
					percentage = slideQuestionDtos.size();
					if (slideQuestionDtos.get(0).getQuestionName().contains("Please confirm that your Email ID"))
						percentage = ((quesNo) * 100) / percentage;
					else
						percentage = ((quesNo - 1) * 100) / percentage;
				}

				if (slideQuestionDtos != null && !slideQuestionDtos.isEmpty()) {
					if (slideQuestionDtos.get(0).getQuestionName().contains("Please confirm that your Email ID")
							&& slideQuestionDtos.size() == quesNo) {
						quesNo = quesNo + 1;
					}
				}

				if (quesNo >= 1 && quesNo <= (slideQuestionDtos.size() + 1)) {
					for (SlideQuestionDto dto : slideQuestionDtos) {

						if (quesNo == (Integer.parseInt(dto.getQuestionSeq()))) {
							retDto = dto;
							retDto.setPercent(percentage);
							retDto.setComment(comment);
							logger.info("QuestionData is : " + retDto.toString());
							break;
						}
					}
					model.addAttribute("questionDto", retDto);
					return preissuancejsp + "customer-sec";
				} else if (quesNo == 0) {
					for (SlideQuestionDto dto : slideQuestionDtos) {
						if (1 == (Integer.parseInt(dto.getQuestionSeq()))) {
							retDto = dto;
							retDto.setPercent(0);
							retDto.setComment(comment);
							logger.info("QuestionData is : " + retDto.toString());
							break;
						}
					}
					model.addAttribute("questionDto", retDto);
					return preissuancejsp + "customer-sec";
				} else {
					logger.info("Returning to Finished");
					return buyerFinish(model);
				}
			}
			return preissuancejsp + "customer-sec";
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured : " + ex);
		} finally {
			ThreadContext.pop();
		}
		return preissuancejsp + "customer-sec";

	}

	@RequestMapping(value = "/retrivePrevAns", method = { RequestMethod.POST })
	public @ResponseBody SlideQuestionDto retriveAns(
			@ModelAttribute(value = "slideQuestionDto") SlideQuestionDto slideQuestionDto) {
		String txnId = (String) session.getAttribute(Constants.TXNID);
		SlideQuestionDto retDto = new SlideQuestionDto();
		try {
			if (txnId != null) {
				@SuppressWarnings("unchecked")
				List<SlideQuestionDto> slideQuestionDtos = (List<SlideQuestionDto>) session
						.getAttribute(Constants.SLIDEQUESTIONDTOS);
				int quesNo = Integer.parseInt(slideQuestionDto.getQuestionSeq()) - 1;
				int percentage = 0;
				if (slideQuestionDtos != null && !slideQuestionDtos.isEmpty()) {
					percentage = slideQuestionDtos.size() + 1;
					percentage = (quesNo * 100) / percentage;
				}
				ThreadContext.push("retrivePrevAns : " + txnId);
				logger.info("saveUpdateBuyerRecords : Start : " + slideQuestionDto.toString());
				slideQuestionDto.setQuestionSeq(Integer.toString(quesNo));
				retDto = preissuanceService.getQuestionAns(slideQuestionDto, txnId);
				retDto.setPercent(percentage);
				logger.info("saveUpdateBuyerRecords : End : " + retDto);
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured inside retriveAns :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		return retDto;
	}

	@RequestMapping(value = "buyer-finish", method = { RequestMethod.GET, RequestMethod.POST })
	public String buyerFinish(Model model) {
		// redundency of this method available in sendAnswer
		try {
			String txnId = (String) session.getAttribute(Constants.TXNID);
			final String appSource = (String) session.getAttribute("appSource");
			final List<String> buyerActivityList = (List<String>) session.getAttribute("buyerActivityList");
			if (txnId != null) {
				ThreadContext.push("buyer-finish : " + txnId);
				logger.info("Customer completed all questions");
				final SellerDto sellerDto = (SellerDto) session.getAttribute(Constants.SELLERDTO);
				logger.debug("Buyer finish" + sellerDto);

				if (sellerDto.getCustEmailId() != null && !sellerDto.getCustEmailId().isEmpty()
						&& !"null".equalsIgnoreCase(sellerDto.getCustEmailId())) {
					logger.info("Mail send : start");
					boolean mailSend = preissuanceService.sendBuyerMail(sellerDto, appSource);
					logger.info("Mail send : End : " + mailSend);
				}

				logger.info("send SMS : Start");
				boolean smsSend = preissuanceService.sendBuyerSms(sellerDto);
				logger.info("send SMS : End : " + smsSend);

				logger.info("send feedback SMS : Start");
				final String customerResponse = preissuanceService.sendFeedbackSms(txnId, sellerDto, appSource);

				logger.info("customer response=" + customerResponse);

				ExecutorService execService = Executors.newFixedThreadPool(2);
				List<Callable<Boolean>> list = new ArrayList<>();

				Callable<Boolean> callable = new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return preissuanceService.writePDFFileToOmniMount(sellerDto, customerResponse, appSource); // Added
					}
				};
				list.add(callable);

				Callable<Boolean> callable1 = new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return preissuanceService.writeBuyerResponseActivityLogs(buyerActivityList,
								sellerDto.getTxnId());
					}
				};
				list.add(callable1);
				execService.invokeAll(list);
				execService.shutdown();

				session.removeAttribute(Constants.TXNID);
				session.removeAttribute(Constants.SLIDEQUESTIONDTOS);
				session.removeAttribute(Constants.SELLERDTO);
				session.removeAttribute("buyerActivityList");
				logger.info("Returning to successBuyer");
				return preissuancejsp + "successBuyer";
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured inside buyerFinish :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		session.removeAttribute(Constants.TXNID);
		session.removeAttribute(Constants.SLIDEQUESTIONDTOS);
		session.removeAttribute(Constants.SELLERDTO);
		session.removeAttribute("buyerActivityList");
		logger.info("Un Authorized Access Throwing customer to error page");
		return preissuancejsp + Constants.ERRORPAGE;
	}

	@RequestMapping(value = "conf", method = { RequestMethod.GET, RequestMethod.POST })
	public String buyerConfirmation(@RequestParam(name = Constants.TXNID, required = false) String txnId, Model model) {
		try {
			String txnId1 = "";
			if (txnId != null) {
				try {
					ThreadContext.push("conf : " + txnId);
					if (txnId.contains(" ")) {
						txnId1 = txnId.replaceAll(" ", "+");
					} else {
						txnId1 = txnId;
					}

					String txnIdWithSource = AESEncryptor.decrypt(txnId1);
					String txnIde = txnIdWithSource.split(Pattern.quote("||"))[0];
					String appSource = txnIdWithSource.split(Pattern.quote("||"))[1];

					logger.info("Confirmation Processing start For : " + txnIde);
					if (txnIde != null) {
						List<SlideQuestionDto> slideQuestionDtoList = preissuanceService
								.getSlideQuestionsAnsByCust(txnIde, appSource);
						if (slideQuestionDtoList != null && !slideQuestionDtoList.isEmpty()) {
							logger.info("Data Found are : " + slideQuestionDtoList.toString());
							model.addAttribute("slideQuestionDtoList", slideQuestionDtoList);
							model.addAttribute("txId", txnIde);
							model.addAttribute("custName", slideQuestionDtoList.get(0).getDynamicName().toUpperCase());
							model.addAttribute("date", Commons.getFormatedDate());
						}
						logger.info("Returning to view buyerConfirmation");
						return preissuancejsp + "buyerConfirmation";
					} else {
						logger.info("Un Authorized Access Throwing customer to error page as txn id is null or blank");
						return preissuancejsp + Constants.ERRORPAGE;
					}
				} catch (Exception ex) {
					logger.info("Invalid TX ID : Unable to decript : " + txnId1);
					logger.error("Invalid txnId :: Unable to decrypt :: " + ex);
				}
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : Exception occured inside buyerConfirmation :: " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("Un Authorized Access Throwing customer to error page");
		return preissuancejsp + Constants.ERRORPAGE;
	}

	@RequestMapping(value = "/cancer-2-prev", method = { RequestMethod.POST })
	public String cancerPrev2(Model model, HttpServletRequest request, HttpSession session) {
		String txnId = (String) session.getAttribute("mpro_txn_id");
		// Added By RK01318
		String mproTxnId = (String) request.getSession().getAttribute("mpro_txn_id");
		ThreadContext.push("cancer1-section :" + mproTxnId);
		String category = "Cancer";
		String createdBy = "mpro";
		Map<String, String> mproCancerMap = preissuanceService.getCancerAnswerDetails(category, mproTxnId, createdBy);
		model.addAttribute("cancerSurveyAnswerMap", mproCancerMap);
		// Added By RK01318
		if (txnId != null) {
			/*
			 * preissuanceService.deletePreviousCancerRecords(qstnIds, txnId);
			 */
		}
		ThreadContext.pop();
		return preissuancejsp + "cancer-1";
	}

	@RequestMapping(value = "/generatenewotp", method = { RequestMethod.POST })
	public @ResponseBody String generateNewOtp(Model model, HttpServletRequest request) {
		String message = "";
		String otpmessage = "";
		Boolean isSuccess = true;
		String appSource = session.getAttribute("appSource") + "";
		ThreadContext.push("generateNewOtp -section :");
		try {
			otpmessage = request.getReader().readLine();
			if (otpmessage.contains("Please try again after 1 hour !")) {
				isSuccess = false;
			}
		} catch (IOException e) {
			logger.error("Failed to fetch the otp message." + e);
		}

		if (isSuccess) {
			String random = "1234";
			// String otpstatus = preissuanceDao.isOtpExpireOrNot();
			// final String random = Commons.getOtp();
			Map<String, PiSellerTxnDto> sellerMproDtoMap = (Map<String, PiSellerTxnDto>) session
					.getAttribute("seller_mpro_dto");
			Set<String> sellerMproKeySet = sellerMproDtoMap.keySet();
			Iterator<String> keyIterator = sellerMproKeySet.iterator();
			String transactions[] = new String[2];
			int i = 0;
			while (keyIterator.hasNext()) {
				transactions[i] = keyIterator.next();
				PiSellerTxnDto sellerMproDto = sellerMproDtoMap.get(transactions[i]);

				if (sellerMproDto != null) {
					// final String random;

					OTPRequiredDTO otpDetails = preissuanceDao.isOtpExpireOrNot(sellerMproDto.getTxnId());
					if (otpDetails.getOtpNumber().equalsIgnoreCase("exceedOneHour")) {
						// random = Commons.getOtp();
						otpDetails.setOtp_recieve_timestamp(0);
						// random = "1234";

					} else {
						random = otpDetails.getOtpNumber();

					}
					logger.info("re-generating the otp.");

					if (preissuanceService.generateNewOtpAndUpdate(sellerMproDto, appSource, random,
							otpDetails.getOtp_recieve_timestamp())) {
						message = "new otp sent.";
					} else {
						message = "error! please try again after 1 hour.";
					}
				}
				i++;
			}
		}
		ThreadContext.pop();
		return message;
	}

	@RequestMapping(value = "mproconf", method = { RequestMethod.GET, RequestMethod.POST })
	public String mproBuyerConfirmation(@RequestParam(name = Constants.TXNID, required = false) String txnId,
			Model model) {
		logger.info("PreissuanceControllerMVC : mproBuyerConfirmation() : START");
		String redirectionPath = preissuancejsp + "mproErrorPage";
		try {
			String txnId1;
			if (txnId != null) {
				if (txnId.contains(" ")) {
					txnId1 = txnId.replaceAll(" ", "+");
				} else {
					txnId1 = txnId;
				}
				String txnIdWithSource = AESEncryptor.decrypt(txnId1);
				String txnIde = txnIdWithSource.split(Pattern.quote("||"))[0];
				String appSource = txnIdWithSource.split(Pattern.quote("||"))[2];

				// @RK01318 Starts
				boolean isWopInsured = preissuanceService.isInsurerApplicable(txnIde);
				session.setAttribute("isWopInsured", isWopInsured);
				// @RK01318 Ends

				ThreadContext.push("mproconf : " + txnIde);
				logger.info(
						"PreissuanceControllerMVC : getAllMproQuestionWithAnswers() for txnid: " + txnIde + " : START");
				List<MproBuyerResponseObj> questionList = preissuanceService.getAllMproQuestionWithAnswers(txnIde);
				// Added BY RK01318 ENDS

				logger.info(
						"PreissuanceControllerMVC : getAllMproQuestionWithAnswers() for txnid: " + txnIde + " : END");
				logger.info("PreissuanceControllerMVC : getPiSellerTxnData() for txnid: " + txnIde + " : START");
				PiSellerTxnDto piSellerDto = preissuanceService.getPiSellerTxnData(txnIde, null, appSource).get(txnIde);
				logger.info("PreissuanceControllerMVC : getPiSellerTxnData() for txnid: " + txnIde + " : END");
				String selfiEnableChannelName = env.getProperty("com.qc.preissuance.imagecapture.channelname");

				if (questionList != null && !questionList.isEmpty() && piSellerDto != null
						&& !selfiEnableChannelName.equalsIgnoreCase(preissuanceDao.checkAgtChannelCode(txnIde))) {
					model.addAttribute("questionList", questionList);
					model.addAttribute("txId", txnIde);
					model.addAttribute("custName", preissuanceService.getCustomerName(piSellerDto));
					model.addAttribute("date", Commons.getFormatedDate());
					model.addAttribute("policynumber",
							StringUtility.checkStringNullOrBlankWithoutCase(piSellerDto.getMproPolicyNumber()));
					redirectionPath = preissuancejsp + "mprobuyerConfirmation";
					logger.info("model set and redirecting to mprobuyerConfirmation.");
				} else if (questionList != null && !questionList.isEmpty() && piSellerDto != null
						&& selfiEnableChannelName.equalsIgnoreCase(preissuanceDao.checkAgtChannelCode(txnIde))) {
					model.addAttribute("questionList", questionList);
					model.addAttribute("txId", txnIde);
					model.addAttribute("custName", preissuanceService.getCustomerName(piSellerDto));
					model.addAttribute("date", Commons.getFormatedDate());
					model.addAttribute("policynumber",
							StringUtility.checkStringNullOrBlankWithoutCase(piSellerDto.getMproPolicyNumber()));
					session.setAttribute("selfiImage", preissuanceDao.getcustomerImage(txnIde));
					redirectionPath = preissuancejsp + "mprobuyerConfirmation";
					logger.info("model set and redirecting to mprobuyerConfirmation.");
				}
			}
		} catch (Exception ex) {
			logger.error("Something went wrong : " + ex);
		} finally {
			ThreadContext.pop();
		}
		logger.info("PreissuanceControllerMVC : mproBuyerConfirmation() : END");
		return redirectionPath;
	}

	// ADDED BY RK01318 STARTS
	public void addErrorDataInModel(Model model, MProBuyerDetailsDTO mProBuyerDetailsDTO, HttpServletRequest request) {
		APIErrorDTO errordto = new APIErrorDTO();
		String erorCount = request.getParameter("errorcount");
		if (erorCount == null || "".equals(erorCount.trim())) {
			erorCount = "0";
		}
		Integer errorCountTemp = Integer.parseInt(erorCount);
		errorCountTemp++;
		errordto.setErrorCode(500);
		errordto.setErrorcount(errorCountTemp);
		model.addAttribute("error", errordto);
	}
	// ADDED BY RK01318 ENDS

	// ADDED BY @RK01318 STARTS FOR AXIS POLICY STARTS
	public void insertPSMQuestions(String mproTxnId, String isPrev, boolean isQuestionAndAnswerNull, Model model) {
		Date currentDate = new java.util.Date();
		String category = "Psm";
		String createdBy = "mpro";
		String platform = request.getParameter("platform");
		String userAgent = request.getParameter("userAgent");
		String forward = request.getParameter("forward");
		boolean isQuestionAndAnswerNull1 = isQuestionAndAnswerNull;
		MProBuyerDetailsDTO mProBuyerDetailsDTO = new MProBuyerDetailsDTO();

		List<MProBuyerDetailsDTO> questionList = new ArrayList<MProBuyerDetailsDTO>();
		String psm1 = request.getParameter("PSM1");
		String ansPsm1 = request.getParameter("A_PSM1");
		mProBuyerDetailsDTO.setTXNID(mproTxnId);
		mProBuyerDetailsDTO.setQSTN_NAME(psm1);
		mProBuyerDetailsDTO.setANSWER(ansPsm1);
		// ADDED BY RK01318 STARTS
		if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
				|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
			isQuestionAndAnswerNull1 = true;
			// ADDED BY RK01318 7-March-2019 STARTS
			addErrorDataInModel(model, mProBuyerDetailsDTO, request);
			// ADDED BY RK01318 7-March-2019 ENDS
		}
		// ADDED BY RK01318 ENDS
		mProBuyerDetailsDTO.setCREATED_DT(currentDate);
		mProBuyerDetailsDTO.setQSTN_CAT(category);
		mProBuyerDetailsDTO.setQSTN_ID("PSM1");
		mProBuyerDetailsDTO.setCREATED_BY(createdBy);
		mProBuyerDetailsDTO.setQuestionType("Primary");
		mProBuyerDetailsDTO.setAnswerType("boolean");
		mProBuyerDetailsDTO.setParentId("NA");
		mProBuyerDetailsDTO.setUserAgent(userAgent);
		mProBuyerDetailsDTO.setPlatform(platform);
		questionList.add(mProBuyerDetailsDTO);

		mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
		String psm2 = request.getParameter("PSM2");
		String ansPsm2 = request.getParameter("A_PSM2");
		mProBuyerDetailsDTO.setTXNID(mproTxnId);
		mProBuyerDetailsDTO.setQSTN_NAME(psm2);
		mProBuyerDetailsDTO.setANSWER(ansPsm2);
		// ADDED BY RK01318 STARTS
		if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
				|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
			isQuestionAndAnswerNull1 = true;
			// ADDED BY RK01318 7-March-2019 STARTS
			addErrorDataInModel(model, mProBuyerDetailsDTO, request);
			// ADDED BY RK01318 7-March-2019 ENDS
		}
		// ADDED BY RK01318 ENDS
		mProBuyerDetailsDTO.setCREATED_DT(currentDate);
		mProBuyerDetailsDTO.setQSTN_CAT(category);
		mProBuyerDetailsDTO.setQSTN_ID("PSM2");
		mProBuyerDetailsDTO.setCREATED_BY(createdBy);
		mProBuyerDetailsDTO.setQuestionType("Primary");
		mProBuyerDetailsDTO.setAnswerType("boolean");
		mProBuyerDetailsDTO.setParentId("NA");
		mProBuyerDetailsDTO.setUserAgent(userAgent);
		mProBuyerDetailsDTO.setPlatform(platform);
		questionList.add(mProBuyerDetailsDTO);

		mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
		String psm3 = request.getParameter("PSM3");
		String ansPsm3 = request.getParameter("A_PSM3");
		mProBuyerDetailsDTO.setTXNID(mproTxnId);
		mProBuyerDetailsDTO.setQSTN_NAME(psm3);
		mProBuyerDetailsDTO.setANSWER(ansPsm3);
		// ADDED BY RK01318 STARTS
		if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
				|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
			isQuestionAndAnswerNull1 = true;
			// ADDED BY RK01318 7-March-2019 STARTS
			addErrorDataInModel(model, mProBuyerDetailsDTO, request);
			// ADDED BY RK01318 7-March-2019 ENDS
		}
		// ADDED BY RK01318 ENDS
		mProBuyerDetailsDTO.setCREATED_DT(currentDate);
		mProBuyerDetailsDTO.setQSTN_CAT(category);
		mProBuyerDetailsDTO.setQSTN_ID("PSM3");
		mProBuyerDetailsDTO.setCREATED_BY(createdBy);
		mProBuyerDetailsDTO.setQuestionType("Primary");
		mProBuyerDetailsDTO.setAnswerType("boolean");
		mProBuyerDetailsDTO.setParentId("NA");
		mProBuyerDetailsDTO.setUserAgent(userAgent);
		mProBuyerDetailsDTO.setPlatform(platform);
		questionList.add(mProBuyerDetailsDTO);

		logger.info("Mpro saveMproBuyerDetails for PSM : START");
		if ((!isQuestionAndAnswerNull1 || "true".equals(isPrev)) && !("true".equals(forward))) {
			// ADDED BY RK01318 7-March-2019 STARTS
			APIErrorDTO errordto = new APIErrorDTO();
			model.addAttribute("error", errordto);
			// ADDED BY RK01318 7-March-2019 ENDS
		}

		if (!"true".equals(isPrev) && !isQuestionAndAnswerNull1) {
			preissuanceService.saveMproBuyerDetails(questionList, null, Constants.PSM);
		}
		logger.info("Mpro saveMproBuyerDetails for PSM : END");
	}

	public List<MProBuyerDetailsDTO> repsalesInsertion(String mproTxnId, String isPrev, Model model,
			String redirectionPath) {

		String category = "Rs";
		String createdBy = "mpro";
		String platform = request.getParameter("platform");
		String userAgent = request.getParameter("userAgent");
		Date currentDate = new java.util.Date();
		List<MProBuyerDetailsDTO> questionList = new ArrayList<>();
		MProBuyerDetailsDTO mProBuyerDetailsDTO = new MProBuyerDetailsDTO();
		String rs1 = request.getParameter("RS1");
		String ansRs1 = request.getParameter("A_RS1");
		mProBuyerDetailsDTO.setTXNID(mproTxnId);
		mProBuyerDetailsDTO.setQSTN_NAME(rs1);
		mProBuyerDetailsDTO.setANSWER(ansRs1);
		// ADDED BY RK01318 STARTS
		if (!"true".equals(isPrev) && (StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getQSTN_NAME())
				|| StringUtility.checkFieldIsNull(mProBuyerDetailsDTO.getANSWER()))) {
			// ADDED BY RK01318 7-March-2019 STARTS
			addErrorDataInModel(model, mProBuyerDetailsDTO, request);
			// ADDED BY RK01318 7-March-2019 ENDS

		}
		// ADDED BY RK01318 ENDS
		mProBuyerDetailsDTO.setCREATED_DT(currentDate);
		mProBuyerDetailsDTO.setQSTN_CAT(category);
		mProBuyerDetailsDTO.setQSTN_ID("RS1");
		mProBuyerDetailsDTO.setCREATED_BY(createdBy);
		mProBuyerDetailsDTO.setQuestionType("Primary");
		mProBuyerDetailsDTO.setAnswerType("boolean");
		mProBuyerDetailsDTO.setParentId("NA");
		mProBuyerDetailsDTO.setPlatform(platform);
		mProBuyerDetailsDTO.setUserAgent(userAgent);
		questionList.add(mProBuyerDetailsDTO);

		return questionList;

	}

	public void backflowProcess(final String txnid, final String appsource) // ADD
																			// APPSOURCE
																			// PARAMETER
																			// FOR
																			// POSV
																			// MULTIENV
																			// @RK01318
	{

		// Back Flow Service Code
		logger.info("getBackFlowData : START");
		final List<BackFlowServiceDTO> backFlowDataList = preissuanceService.getBackFlowData(txnid, session);
		logger.info("getBackFlowData : END");

		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				Integer counter = 0;
				for (int i = 0; i < 5; i++) {
					counter++;
					logger.info("mpro back service start calling from controller : START");
					boolean backFlowServiceCall = preissuanceService.mproServiceCall(backFlowDataList, appsource);
					logger.info("mpro back service End calling from controller : END");
					logger.info("calling mpro backflow webservice attempt no :" + counter + " for txnId :" + txnid);
					if (backFlowServiceCall) {
						logger.info("Back Flow service Call successfully for txn_id " + txnid + " ATTEMPT :" + counter);

						boolean backFlowUpdateStatus = preissuanceService.saveBackFlowStatus(txnid);

						if (backFlowUpdateStatus) {
							logger.info("backflow update status successfully for txn id" + txnid);
						} else {
							logger.info("problem in update backflow  status in db for txn id" + txnid);
						}

						break;
					} else {
						try {
							Thread.sleep(10000);
						} catch (Exception e) {
							logger.error("Failed to get result from backflow service." + e, e);
						}
						logger.info("Back Flow service could not  Call  for txn_id " + txnid);
						logger.info("txn id: " + txnid + " failed ATTEMPT : " + counter);
					}
				}

			}
		});
		t.start();
		logger.info("status message success received.");
		Thread pdfCreationThread = new Thread(new Runnable() {
			@Override
			public void run() {
				logger.info("Creating mpro pdf for txnid :" + txnid + " : START");
				preissuanceService.createMproPdf(txnid, appsource);
				logger.info("Creating mpro pdf for txnid :" + txnid + " : END");
			}
		});

		pdfCreationThread.start();

	}

	public void sendSMSandMailAfterOTPConfirmation(String referencekey, String axisTranscation) {

		final List<String> restrictedPlancode = new ArrayList();

		final Map<String, PiSellerTxnDto> sellerDtoMap = (Map<String, PiSellerTxnDto>) request.getSession()
				.getAttribute("seller_mpro_dto");
		final PiSellerTxnDto sellerDto = sellerDtoMap.get(axisTranscation);
		sellerDto.setReferenceKey(referencekey);
		final String appSource = session.getAttribute("appSource") + "";
		Thread smsMailThread = new Thread(new Runnable() {

			@Override
			public void run() {
				if (sellerDto != null) {
					// Send SMS and Mail
					if (sellerDto.getCustEmailId() != null && !sellerDto.getCustEmailId().isEmpty()
							&& !"null".equalsIgnoreCase(sellerDto.getCustEmailId())) {
						logger.info("send Mpro Mail : Start");

						boolean mailSend = preissuanceService.sendMproMail(sellerDto, false, null, appSource,
								sellerDto.getPlanCode());
						logger.info("send Mpro Mail : End : " + mailSend);
					}

					logger.info("send SMS: Start");
					boolean smsSend = preissuanceService.sendMproSms(sellerDto, null, false, appSource,
							sellerDto.getPlanCode());
					logger.info("send SMS: End : " + smsSend);
					if (smsSend) {
						logger.info("Updating data in DB For SMS: Start");
						boolean smsUpdateStatus = preissuanceService.updateSellerMproRecords(sellerDto, "Y");
						logger.info("Updating data in DB For SMS: End : " + smsUpdateStatus);
					}
				}
			}
		});
		smsMailThread.start();
	}

	public boolean errorInGetMethod(HttpServletRequest request) {

		if (("GET".equalsIgnoreCase(request.getMethod())) && (!("true".equalsIgnoreCase(request.getParameter("isPrev")))
				&& !("true".equalsIgnoreCase(request.getParameter("isPrevtemp")))
				&& !("true".equalsIgnoreCase(request.getParameter("forward"))))) {
			logger.info("User have use URL:- " + request.getRequestURL() + " Directly");
			return true;

		} else {
			return false;
		}

	}

}