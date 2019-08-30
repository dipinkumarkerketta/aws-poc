<!DOCTYPE html>
<html>
    <head>
        <title>Max Form</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
       	<link href="css/bootstrap.css" rel="stylesheet" />
		<link href="css/nice-select.css" rel="stylesheet" />
	<link href="css/style_mpro.css" rel="stylesheet" />   
    </head>
    <body>
   
	    <%
		response.setHeader("Cache-Control","no-cache,no-store"); 
		response.setHeader("Pragma","no-cache"); 
		response.setHeader("Pragma","no-store"); 
		response.setDateHeader ("Expires", -1); 
		String totalQstns= session.getAttribute("TotalQstns").toString();
		String attemptedQstns = session.getAttribute("AttemptedQstns")+"";
		%>
    
        <header>
            <div class="container-fluid">
                <div class="logo"><a href="#"><img src="images/logo.png" alt="MAX_Life"/></a></div>
            </div>
        </header>
        <section class="main-content">
            <div class="container">
                <label for="totalQstns">Total Questions: <%out.print(totalQstns);%></label>
                <label for="attemptedQstns" class="pull-right">Attempted Questions: <%out.print(attemptedQstns);%></label>
                <h1>Product Related Questions  <%=session.getAttribute("customerBenifitName")!=null?"("+session.getAttribute("customerBenifitName")+")":"" %></h1>
                <form id="product_1" class="cstm-form" action="product-1" method="post" name="mainForm" id="mainForm">
                    <!-- Ques 1 -->
                    <div class="form-group radio-btn clearfix">
                       <label>${productQuestionDto.questionName}</label>
                        <div class="col-sm-12">
                            <ul class="radio-one">
                                <li id="yes">
                                    <img class="right-tick" src="images/right-tick.png" alt="radio-select"> 
                                    <input type="radio" name="A_PROD" onClick="onClickRadioButton()" value="Y" id="radio-one" >
                                    <label for="radio-one">Yes</label>
                                </li>
                                <li id="no">
                                    <img class="right-tick" src="images/right-tick.png" alt="radio-select"> 
                                    <input type="radio" name="A_PROD" onClick="onClickRadioButton()" value="N" id="radio-two">
                                    <label for="radio-two">No</label>
                                </li>
                            </ul>
                        </div>
                    </div>
                    
                    
                    <div class="sbmt-btn" >
                    	<div class="cstm-error" id="show_msg"></div>
                       	<button class="btn btn-primary" type="button" id="prevButton" onclick="prevPage();">Prev</button>
                        <button class="btn btn-primary" type="button" id="nextButton" onclick="validate();">Next</button>
                    </div>
                    
                    <!--Added By Rohit Starts--> 
                    <input type="hidden" name="isPrev" id="isPrev" value="false"/>
                    <input type="hidden"  id="currentAnswer"  value="${currentMProBuyerDetailsDTO.ANSWER}"/>
                    <input type="hidden"  id="QuestionSeq"  value="${question_pos}"/>
                    <input type="hidden" name="platform"  id="platform"  value="">
                    <input type="hidden" name="userAgent" id="userAgent"  value="">
                    <!-- ADDED ON 7-March-2019 -->
                    <input type="hidden" name="errorCode" id="errorCode" value="${error.errorCode}">
                    <input type="hidden" name="errorcount" id="errorcount" value="${error.errorcount}">
                    <!-- ADDED ON 7-March-2019 -->
                    <!--Added By Rohit Ends-->
                    <!-- Added By RK01318 for Axis Policy Starts -->
                    <input type="hidden" name="transactionCount" id="transactionCount" value="${transactionCount}">
                    <input type="hidden" name="axispolicyOrNot" id="axispolicyOrNot" value="${axispolicyOrNot}">
                    <!-- Added By RK01318 for Axis Policy Ends   -->
                </form>
                <!--Added By Amit Starts-->
                <form action="product-1" class="custom-form" method="POST" name="prevForm" id="prevForm">
                <input type="hidden" name="isPrev" id="isPrev" value="true"/>
                </form>
    			<!--Added By Amit Ends-->
            </div>
        </section>
        
        <footer>
            <div class="container-fluid"><img src="images/bottom-footer.jpg" alt="Max-life"/></div>
        </footer>
        
          
        <script src="./preissuance/js/jquery-1.11.3.min.js"></script>
		<script src="js/bootstrap.js"></script>
		<script src="js/jquery.nice-select.min.js"></script>
        
        <script>
			
          $(document).ready(function() {  
              
        	   //Added BY RK01318 7-FEB-2019 Starts 
        	   if($('#errorcount').val()>1)
	            {
	           	alert("Dear customer. we are experiencing slowness in our system, please retry after sometime or contact your seller.");
	            }
	           else if($('#errorCode').val()=='500')
	            {
	           	alert("Dear Customer, we are experiencing slowness in our system, please retry submitting your response now.");
	            }
               //Added By RK01318 7-FEB-2019 Ends
        	 
            $(".radio-btn li").click(function() {
                $(this).addClass('active');
                $('.radio-btn li').on('click', function() {
                    $(this).addClass('active').siblings().removeClass('active');
                });
            });
            
            //Added By RK01318 Starts
            var platform=navigator.platform;
            var userAgent=navigator.userAgent;
            $('#platform').val(platform);
            $('#userAgent').val(userAgent);
            //Added By RK01318 Ends
            
            var currentanswer=$("#currentAnswer").val();
            if("Y"==currentanswer)
            {
            	$("#radio-one").click();
            }
            else if("N"==currentanswer)
            {
            	$("#radio-two").click();
            }
            
            var questionSeq=$("#QuestionSeq").val();
            //Added BY @RK01318 for Axis Policy
          //Added BY @RK01318 for Axis Policy
            if($('#axispolicyOrNot').val()=="true")
            {
	            if("0"==questionSeq  &&  $('#transactionCount').val()=="1")
	            {
	            	$("#prevButton").hide();
	            }
            }
            else
            {
            	if("0"==questionSeq )
	            {
	            	$("#prevButton").hide();
	            }
            }
            
            
            
          
            
          });
          
          function onClickRadioButton(){
        	  $("#show_msg").html(" ");
          }
          
          function validateForm() {
        	    var radios = document.getElementsByName("A_PROD");
        	    var formValid = false;

        	    var i = 0;
        	    while (!formValid && i < radios.length) {
        	        if (radios[i].checked) formValid = true;
        	        i++;        
        	    }
        	    
        	    if (!formValid){
        	    	 $("#show_msg").html("Pls answer all the mandatory questions (marked with *)");        	    	
        	    }
        	    return formValid;
        	}
          
          function prevPage()
          {
      		document.prevForm.submit();
          }
          
          function validate()
          {
          	var isValidated = validateForm();
          	if(isValidated){
          		$("#nextButton").attr("disabled", true);
          		document.mainForm.submit();
     		}
          	else{
     		 	$("#show_msg").html("Pls answer all the mandatory questions (marked with *)");
     		 }
          }
        </script>
    </body>
</html>